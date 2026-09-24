package com.osrsevents;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Deque;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

/**
 * The sidebar view of what the chat lines say once: whether the plugin is
 * connected, which of the account's characters is logged in, what it is
 * watching and what it reported lately. The two lists are full-width tables
 * in the style of the world hopper. Every setter may be called from any
 * thread; the Swing work happens on the event thread.
 */
class OsrsEventsPanel extends PluginPanel
{
	private static final int MAX_RECENT = 15;
	private static final int MAX_TARGETS_SHOWN = 20;
	private static final int LEFT_COLUMN = 34;
	private static final int RIGHT_COLUMN = 64;
	/** A place on a podium. */
	static final Color GOLD = new Color(0xE8B923);
	private static final int ROW_HEIGHT = 22;
	private static final int TEXT_WIDTH = 190;
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

	/** One reported thing in the Recent table. */
	private static final class Recent
	{
		final String time;
		final String what;
		final String where;
		final String result;
		final Color colour;

		Recent(String what, String where, String result, Color colour)
		{
			this.time = LocalTime.now().format(TIME);
			this.what = what;
			this.where = where;
			this.result = result;
			this.colour = colour;
		}
	}

	private final JLabel status = wrapped("Starting…", Color.WHITE);
	private final JLabel character = wrapped("Not logged in", Color.WHITE);
	private final JLabel characterDetail = wrapped("", ColorScheme.LIGHT_GRAY_COLOR);
	private final JPanel events = column();
	private final JPanel recent = column();
	private final JPanel races = column();
	private final JPanel raceSection = column();
	private final Deque<Recent> recentRows = new ArrayDeque<>();
	/** Events whose targets are shown; the rest are folded. Survives a refresh. */
	private final Set<String> expanded = new HashSet<>();
	private List<ApiModels.EventInfo> shownEvents;
	private List<ApiModels.OtherEvent> shownOthers;

	OsrsEventsPanel(Runnable checkConnection)
	{
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(0, 0, 10, 0));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel content = column();

		JPanel top = column();
		top.setBorder(new EmptyBorder(10, 10, 6, 10));
		top.add(status);
		top.add(spacer(6));
		JButton check = new JButton("Check connection");
		check.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		check.addActionListener(e -> checkConnection.run());
		check.setAlignmentX(LEFT_ALIGNMENT);
		check.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		top.add(check);
		content.add(top);

		JPanel who = column();
		who.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		who.setBorder(new EmptyBorder(8, 10, 8, 10));
		character.setFont(FontManager.getRunescapeBoldFont());
		who.add(character);
		who.add(characterDetail);
		content.add(who);

		content.add(spacer(10));
		content.add(header("#", "Events", "Open"));
		content.add(events);

		raceSection.add(spacer(10));
		raceSection.add(header("Rank", "Races", "Gained"));
		raceSection.add(races);
		raceSection.setVisible(false);
		content.add(raceSection);

		content.add(spacer(10));
		content.add(header("Time", "Recent", "Result"));
		content.add(recent);

		add(content, BorderLayout.NORTH);
		setEvents(null, null);
		renderRecent();
	}

	/** @param ok green when true, red when false, grey when null: nothing wrong, nothing to do yet */
	void setStatus(String text, Boolean ok)
	{
		SwingUtilities.invokeLater(() ->
		{
			status.setText(html("● " + text));
			status.setForeground(ok == null ? ColorScheme.LIGHT_GRAY_COLOR : ok ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.PROGRESS_ERROR_COLOR);
		});
	}

	/** @param detail under the name: which role it has, or why drops from it are not sent */
	void setCharacter(String name, String detail)
	{
		SwingUtilities.invokeLater(() ->
		{
			character.setText(html(name));
			characterDetail.setText(html(detail));
		});
	}

	/** @param others events that are not running; the races among them go to the race table */
	void setEvents(List<ApiModels.EventInfo> list, List<ApiModels.OtherEvent> others)
	{
		SwingUtilities.invokeLater(() ->
		{
			shownEvents = list;
			shownOthers = others;
			renderEvents();
		});
	}

	/**
	 * Every event the player is in, one row each, with its open squares or
	 * tiles folded under it. An event with nothing open still gets its row,
	 * so the list says which events the account plays and not only which
	 * have work left. The chevron folds; the title opens the event.
	 */
	private void renderEvents()
	{
		events.removeAll();

		List<ApiModels.EventInfo> list = shownEvents;
		boolean anyOther = shownOthers != null && shownOthers.stream().anyMatch(o -> !o.isRace());
		if ((list == null || list.isEmpty()) && !anyOther)
		{
			events.add(row(0, "", "Not in any running bingo or board", "", ColorScheme.LIGHT_GRAY_COLOR, null));
		}
		else if (list != null)
		{
			for (ApiModels.EventInfo event : list)
			{
				List<ApiModels.Target> targets = event.targets == null ? Collections.emptyList() : event.targets;
				boolean open = expanded.contains(event.id);
				events.add(eventRow(event, targets.size(), open));

				if (!open)
				{
					continue;
				}
				if (targets.isEmpty())
				{
					events.add(row(0, "", "Nothing the plugin can complete", "", ColorScheme.LIGHT_GRAY_COLOR,
						"No open square or tile that links a wiki page"));
					continue;
				}

				int shown = Math.min(targets.size(), MAX_TARGETS_SHOWN);
				for (int i = 0; i < shown; i++)
				{
					ApiModels.Target target = targets.get(i);
					String label = target.label != null ? target.label : String.valueOf(target.name);
					events.add(row(i,
						"bingo_square".equals(target.kind) ? "" : String.valueOf(target.position),
						label,
						need(target),
						ColorScheme.LIGHT_GRAY_COLOR,
						target.name != null && !target.name.equals(label) ? label + " (" + target.name + ")" : label));
				}
				if (targets.size() > shown)
				{
					events.add(row(shown, "", "+ " + (targets.size() - shown) + " more on the site", "", ColorScheme.LIGHT_GRAY_COLOR, null));
				}
			}
		}

		if (shownOthers != null)
		{
			shownOthers.stream().filter(o -> !o.isRace()).forEach(o -> events.add(otherRow(o)));
		}

		events.revalidate();
		events.repaint();
	}

	/**
	 * An event that is not running, in grey: it claims nothing, but it is
	 * listed so a player sees the event their clan set up before it starts,
	 * while it is paused, and for a week after it ended. Opens on the site.
	 */
	private JComponent otherRow(ApiModels.OtherEvent other)
	{
		String right;
		Color colour = ColorScheme.LIGHT_GRAY_COLOR;
		String tooltip;

		if ("upcoming".equals(other.status))
		{
			String starts = endDate(other.startsAt);
			right = starts == null ? "Soon" : starts;
			tooltip = starts == null ? "Has not started yet" : "Starts " + starts;
		}
		else if ("paused".equals(other.status))
		{
			right = "Paused";
			tooltip = "Paused by the host: nothing is claimed until it resumes";
		}
		else if (other.finish != null)
		{
			right = OsrsEventsPlugin.ordinal(other.finish.place);
			colour = GOLD;
			tooltip = "Ended - you finished " + right + (other.finish.team != null ? " with " + other.finish.team : "");
		}
		else if (other.isRace() && other.rank != null)
		{
			right = other.rank + "/" + (other.entrants == null ? "?" : other.entrants);
			tooltip = "Ended - final rank " + right;
		}
		else
		{
			right = "Ended";
			tooltip = "Ended";
		}

		JPanel row = row(-1, "", String.valueOf(other.title), right, colour, null);
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		((JLabel) ((BorderLayout) row.getLayout()).getLayoutComponent(BorderLayout.CENTER)).setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		linkRow(row, other.url, other.title, tooltip, ColorScheme.DARKER_GRAY_COLOR);

		return row;
	}

	/** Opens the event on the site on a click, with the hover every clickable row has. */
	private static void linkRow(JPanel row, String url, String title, String tooltip, Color background)
	{
		if (url != null && OsrsEventsApi.baseUrl(url) != null)
		{
			row.setToolTipText(tooltip + " - click to open " + title + " on the site");
			row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			row.addMouseListener(hover(row, () -> LinkBrowser.browse(url), background));
		}
		else
		{
			row.setToolTipText(tooltip);
		}
	}

	/** @param list null hides the table: a server that predates races says nothing about them */
	void setRaces(List<ApiModels.Race> list, List<ApiModels.OtherEvent> others)
	{
		SwingUtilities.invokeLater(() ->
		{
			races.removeAll();
			raceSection.setVisible(list != null);
			boolean anyOther = others != null && others.stream().anyMatch(ApiModels.OtherEvent::isRace);

			if (list != null && list.isEmpty() && !anyOther)
			{
				races.add(row(0, "", "Not in any running race", "", ColorScheme.LIGHT_GRAY_COLOR, null));
			}
			if (list != null)
			{
				for (ApiModels.Race race : list)
				{
					races.add(raceRow(race));
					races.add(row(0, "", raceDetail(race), "", ColorScheme.LIGHT_GRAY_COLOR,
						race.live > 0 ? amount(race.live, race.unit) + " of it reported live by the plugin" : null));
				}
			}
			if (list != null && others != null)
			{
				others.stream().filter(ApiModels.OtherEvent::isRace).forEach(o -> races.add(otherRow(o)));
			}

			races.revalidate();
			races.repaint();
		});
	}

	/** Title, rank of entrants and what the account gained; opens the race on the site. */
	private JComponent raceRow(ApiModels.Race race)
	{
		String rank = race.rank == null ? "-" : race.rank + "/" + race.entrants;
		JPanel row = row(-1, rank, String.valueOf(race.title), amount(race.gained, race.unit), ColorScheme.BRAND_ORANGE, null);
		row.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
		BorderLayout layout = (BorderLayout) row.getLayout();
		((JLabel) layout.getLayoutComponent(BorderLayout.CENTER)).setFont(FontManager.getRunescapeBoldFont());
		((JLabel) layout.getLayoutComponent(BorderLayout.WEST)).setForeground(Color.WHITE);

		String what = "DROP_RACE".equals(race.type) ? "Drop race" : "Skill race";
		if (race.url != null && OsrsEventsApi.baseUrl(race.url) != null)
		{
			row.setToolTipText(what + " - click to open " + race.title + " on the site");
			row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			row.addMouseListener(hover(row, () -> LinkBrowser.browse(race.url)));
		}
		else
		{
			row.setToolTipText(what);
		}

		return row;
	}

	/** "Zulrah · 309 behind · ends 30 Sep" */
	private static String raceDetail(ApiModels.Race race)
	{
		StringBuilder detail = new StringBuilder(String.valueOf(race.metric));
		if (race.rank != null && race.rank == 1)
		{
			detail.append(" · leading");
		}
		else if (race.leader != null)
		{
			detail.append(" · ").append(amount(Math.max(race.leader - race.gained, 0), race.unit)).append(" behind");
		}
		String ends = endDate(race.endsAt);
		if (ends != null)
		{
			detail.append(" · ends ").append(ends);
		}
		return detail.toString();
	}

	/** Kills as they are, xp shortened: "309 kc", "1.35M xp". */
	static String amount(long value, String unit)
	{
		if (!"xp".equals(unit))
		{
			return value + " kc";
		}
		if (value >= 1_000_000)
		{
			return String.format(Locale.ROOT, "%.2fM xp", value / 1_000_000d);
		}
		if (value >= 10_000)
		{
			return (value / 1000) + "K xp";
		}
		return value + " xp";
	}

	private static String endDate(String iso)
	{
		if (iso == null)
		{
			return null;
		}
		try
		{
			return OffsetDateTime.parse(iso).format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH));
		}
		catch (DateTimeParseException e)
		{
			return null;
		}
	}

	/** @param colour of the result: approved, waiting, rejected or plain progress */
	void addRecent(String what, String where, String result, Color colour)
	{
		Recent line = new Recent(what, where, result, colour);
		SwingUtilities.invokeLater(() ->
		{
			recentRows.addFirst(line);
			while (recentRows.size() > MAX_RECENT)
			{
				recentRows.removeLast();
			}
			renderRecent();
		});
	}

	private void renderRecent()
	{
		recent.removeAll();
		if (recentRows.isEmpty())
		{
			recent.add(row(0, "", "No claims yet this session", "", ColorScheme.LIGHT_GRAY_COLOR, null));
		}
		int i = 0;
		for (Recent line : recentRows)
		{
			recent.add(row(i++, line.time, line.what, line.result, line.colour, line.what + " in " + line.where));
		}
		recent.revalidate();
		recent.repaint();
	}

	/** "5×" for a target that takes five reports, "100" for a stack of at least 100, else nothing. */
	private static String need(ApiModels.Target target)
	{
		if (target.requiredCount > 1)
		{
			return target.requiredCount + "×";
		}
		if (target.minQuantity > 1)
		{
			return String.valueOf(target.minQuantity);
		}
		return "";
	}

	/**
	 * The event's own row. The chevron on the left folds its targets in or
	 * out; the rest of the row opens the event on the site. The right column
	 * counts what is open.
	 */
	private JComponent eventRow(ApiModels.EventInfo event, int open, boolean expandedNow)
	{
		String type = "BINGO".equals(event.type) ? "Bingo" : "SNAKES_LADDERS".equals(event.type) ? "Board" : "Event";
		// A finish outranks the open count: winning is the news.
		String right = event.finish != null
			? OsrsEventsPlugin.ordinal(event.finish.place) + (event.finish.provisional ? "?" : "")
			: open + " open";
		JPanel row = row(-1, expandedNow ? "▾" : "▸", String.valueOf(event.title), right,
			event.finish != null ? GOLD : open > 0 ? ColorScheme.BRAND_ORANGE : ColorScheme.LIGHT_GRAY_COLOR, null);
		row.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
		BorderLayout layout = (BorderLayout) row.getLayout();
		JLabel chevron = (JLabel) layout.getLayoutComponent(BorderLayout.WEST);
		JLabel title = (JLabel) layout.getLayoutComponent(BorderLayout.CENTER);
		title.setFont(FontManager.getRunescapeBoldFont());
		chevron.setForeground(Color.WHITE);

		chevron.setToolTipText(expandedNow ? "Hide what is open" : "Show what is open");
		chevron.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		chevron.addMouseListener(hover(row, () ->
		{
			if (!expanded.remove(event.id))
			{
				expanded.add(event.id);
			}
			renderEvents();
		}));

		String tooltip = type + ", " + open + " open";
		if (event.finish != null)
		{
			tooltip += " - " + (event.finish.team != null ? event.finish.team : "you") + " finished " + OsrsEventsPlugin.ordinal(event.finish.place)
				+ (event.finish.provisional ? ", for now: claims ahead are still in review" : "");
		}
		linkRow(row, event.url, event.title, tooltip, ColorScheme.MEDIUM_GRAY_COLOR);

		return row;
	}

	private static MouseAdapter hover(JPanel row, Runnable onClick)
	{
		return hover(row, onClick, ColorScheme.MEDIUM_GRAY_COLOR);
	}

	/** A click action with the hover colour every clickable row shows. */
	private static MouseAdapter hover(JPanel row, Runnable onClick, Color background)
	{
		return new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				onClick.run();
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				row.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				row.setBackground(background);
			}
		};
	}

	/** The column titles above a table. */
	private static JPanel header(String left, String centre, String right)
	{
		JPanel row = row(-1, left, centre, right, ColorScheme.LIGHT_GRAY_COLOR, null);
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		for (java.awt.Component c : row.getComponents())
		{
			c.setFont(FontManager.getRunescapeBoldFont());
		}
		return row;
	}

	/**
	 * One full-width table row: a narrow left column, the text, and a
	 * right-aligned value. Rows alternate like the world hopper's. The text
	 * is cut with an ellipsis; the tooltip carries all of it.
	 */
	private static JPanel row(int index, String left, String centre, String right, Color rightColour, String tooltip)
	{
		JPanel row = new JPanel(new BorderLayout())
		{
			@Override
			public Dimension getMaximumSize()
			{
				return new Dimension(Integer.MAX_VALUE, ROW_HEIGHT);
			}
		};
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setPreferredSize(new Dimension(0, ROW_HEIGHT));
		row.setBorder(new EmptyBorder(0, 8, 0, 8));
		row.setBackground(index % 2 == 0 ? ColorScheme.DARK_GRAY_COLOR : ColorScheme.DARKER_GRAY_COLOR);

		JLabel l = cell(left, ColorScheme.LIGHT_GRAY_COLOR, SwingConstants.LEFT);
		l.setPreferredSize(new Dimension(LEFT_COLUMN, ROW_HEIGHT));
		JLabel c = cell(centre, Color.WHITE, SwingConstants.LEFT);
		JLabel r = cell(right, rightColour, SwingConstants.RIGHT);
		r.setPreferredSize(new Dimension(RIGHT_COLUMN, ROW_HEIGHT));

		row.add(l, BorderLayout.WEST);
		row.add(c, BorderLayout.CENTER);
		row.add(r, BorderLayout.EAST);

		if (tooltip != null)
		{
			row.setToolTipText(tooltip);
		}

		return row;
	}

	private static JLabel cell(String text, Color colour, int alignment)
	{
		JLabel label = new JLabel(text == null ? "" : text, alignment);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(colour);
		return label;
	}

	private static JPanel column()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		panel.setAlignmentX(LEFT_ALIGNMENT);
		return panel;
	}

	private static JComponent spacer(int height)
	{
		JPanel gap = new JPanel();
		gap.setOpaque(false);
		gap.setAlignmentX(LEFT_ALIGNMENT);
		gap.setPreferredSize(new Dimension(0, height));
		gap.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
		return gap;
	}

	private static JLabel wrapped(String text, Color colour)
	{
		JLabel label = new JLabel(html(text));
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(colour);
		label.setAlignmentX(LEFT_ALIGNMENT);
		return label;
	}

	/** A fixed width makes a Swing label wrap instead of widening the sidebar. */
	private static String html(String text)
	{
		String escaped = text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
		return "<html><body style='width: " + TEXT_WIDTH + "px'>" + escaped + "</body></html>";
	}
}
