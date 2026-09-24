package com.osrsevents;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
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
	private static final int RIGHT_COLUMN = 52;
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
	private final Deque<Recent> recentRows = new ArrayDeque<>();

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
		content.add(header("#", "Watching", "Need"));
		content.add(events);

		content.add(spacer(10));
		content.add(header("Time", "Recent", "Result"));
		content.add(recent);

		add(content, BorderLayout.NORTH);
		setEvents(null);
		renderRecent();
	}

	void setStatus(String text, boolean ok)
	{
		SwingUtilities.invokeLater(() ->
		{
			status.setText(html("● " + text));
			status.setForeground(ok ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.PROGRESS_ERROR_COLOR);
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

	void setEvents(List<ApiModels.EventInfo> list)
	{
		SwingUtilities.invokeLater(() ->
		{
			events.removeAll();

			boolean any = false;
			if (list != null)
			{
				for (ApiModels.EventInfo event : list)
				{
					if (event.targets == null || event.targets.isEmpty())
					{
						continue;
					}
					any = true;
					events.add(eventRow(event));

					int shown = Math.min(event.targets.size(), MAX_TARGETS_SHOWN);
					for (int i = 0; i < shown; i++)
					{
						ApiModels.Target target = event.targets.get(i);
						String label = target.label != null ? target.label : String.valueOf(target.name);
						events.add(row(i,
							"bingo_square".equals(target.kind) ? "" : String.valueOf(target.position),
							label,
							need(target),
							ColorScheme.LIGHT_GRAY_COLOR,
							target.name != null && !target.name.equals(label) ? label + " (" + target.name + ")" : label));
					}
					if (event.targets.size() > shown)
					{
						events.add(row(shown, "", "+ " + (event.targets.size() - shown) + " more on the site", "", ColorScheme.LIGHT_GRAY_COLOR, null));
					}
				}
			}

			if (!any)
			{
				events.add(row(0, "", "Nothing open to complete", "", ColorScheme.LIGHT_GRAY_COLOR, null));
			}

			events.revalidate();
			events.repaint();
		});
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

	/** The event's own row: its title, and its page on the site on a click. */
	private JComponent eventRow(ApiModels.EventInfo event)
	{
		String type = "BINGO".equals(event.type) ? "Bingo" : "SNAKES_LADDERS".equals(event.type) ? "S&L" : "";
		JPanel row = row(-1, "", String.valueOf(event.title), type, ColorScheme.BRAND_ORANGE, null);
		row.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
		((JLabel) ((BorderLayout) row.getLayout()).getLayoutComponent(BorderLayout.CENTER)).setFont(FontManager.getRunescapeBoldFont());

		if (event.url != null && OsrsEventsApi.baseUrl(event.url) != null)
		{
			row.setToolTipText("Open " + event.title + " on the site");
			row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			row.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					LinkBrowser.browse(event.url);
				}

				@Override
				public void mouseEntered(MouseEvent e)
				{
					row.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					row.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
				}
			});
		}

		return row;
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
