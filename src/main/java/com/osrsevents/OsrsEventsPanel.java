package com.osrsevents;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

/**
 * The sidebar view of what the chat lines say once: whether the plugin is
 * connected, which of the account's characters is logged in, what it is
 * watching and what it reported lately. Every setter may be called from any
 * thread; the Swing work happens on the event thread.
 */
class OsrsEventsPanel extends PluginPanel
{
	private static final int MAX_RECENT = 15;
	private static final int MAX_TARGETS_SHOWN = 12;
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

	private final JLabel status = text("Starting…");
	private final JLabel character = text("Not logged in");
	private final JLabel characterDetail = muted("");
	private final JPanel events = column();
	private final JPanel recent = column();
	private final Deque<String> recentLines = new ArrayDeque<>();

	OsrsEventsPanel(Runnable checkConnection)
	{
		super(false);
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel content = column();

		JButton check = new JButton("Check connection");
		check.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		check.setFocusPainted(true);
		check.addActionListener(e -> checkConnection.run());
		check.setAlignmentX(LEFT_ALIGNMENT);

		content.add(heading("Connection"));
		content.add(status);
		content.add(gap());
		content.add(check);
		content.add(gap());
		content.add(heading("Character"));
		content.add(character);
		content.add(characterDetail);
		content.add(gap());
		content.add(heading("Watching"));
		content.add(events);
		content.add(gap());
		content.add(heading("Recent"));
		content.add(recent);

		add(content, BorderLayout.NORTH);
		setEvents(null);
		renderRecent();
	}

	void setStatus(String text, boolean ok)
	{
		SwingUtilities.invokeLater(() ->
		{
			status.setText(html(text));
			status.setForeground(ok ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.PROGRESS_ERROR_COLOR);
		});
	}

	/** @param detail one line under the name: which role it has, or why drops from it are not sent */
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
					events.add(eventTitle(event));

					int shown = Math.min(event.targets.size(), MAX_TARGETS_SHOWN);
					for (int i = 0; i < shown; i++)
					{
						ApiModels.Target target = event.targets.get(i);
						events.add(muted("• " + (target.label != null ? target.label : String.valueOf(target.name))));
					}
					if (event.targets.size() > shown)
					{
						events.add(muted("+ " + (event.targets.size() - shown) + " more"));
					}
					events.add(gap());
				}
			}

			if (!any)
			{
				events.add(muted("Nothing open that the plugin can complete."));
			}

			events.revalidate();
			events.repaint();
		});
	}

	void addRecent(String line)
	{
		String stamped = LocalTime.now().format(TIME) + "  " + line;
		SwingUtilities.invokeLater(() ->
		{
			recentLines.addFirst(stamped);
			while (recentLines.size() > MAX_RECENT)
			{
				recentLines.removeLast();
			}
			renderRecent();
		});
	}

	private void renderRecent()
	{
		recent.removeAll();
		if (recentLines.isEmpty())
		{
			recent.add(muted("No claims yet this session."));
		}
		for (String line : recentLines)
		{
			recent.add(text(line));
		}
		recent.revalidate();
		recent.repaint();
	}

	/** The event's title, opening its page on the site when it has one. */
	private JComponent eventTitle(ApiModels.EventInfo event)
	{
		JLabel title = text(String.valueOf(event.title));
		title.setFont(FontManager.getRunescapeBoldFont());

		if (event.url != null && event.url.startsWith("https://"))
		{
			title.setToolTipText("Open on the site");
			title.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			title.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					LinkBrowser.browse(event.url);
				}

				@Override
				public void mouseEntered(MouseEvent e)
				{
					title.setForeground(ColorScheme.BRAND_ORANGE);
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					title.setForeground(Color.WHITE);
				}
			});
		}

		return title;
	}

	private static JPanel column()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		panel.setAlignmentX(LEFT_ALIGNMENT);
		return panel;
	}

	private static JLabel heading(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setForeground(ColorScheme.BRAND_ORANGE);
		label.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
		label.setAlignmentX(LEFT_ALIGNMENT);
		return label;
	}

	private static JLabel text(String text)
	{
		JLabel label = new JLabel(html(text));
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(Color.WHITE);
		label.setAlignmentX(LEFT_ALIGNMENT);
		return label;
	}

	private static JLabel muted(String text)
	{
		JLabel label = text(text);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setFont(label.getFont().deriveFont(Font.PLAIN));
		return label;
	}

	private static JComponent gap()
	{
		JPanel gap = new JPanel();
		gap.setBackground(ColorScheme.DARK_GRAY_COLOR);
		gap.setBorder(new EmptyBorder(4, 0, 4, 0));
		gap.setAlignmentX(LEFT_ALIGNMENT);
		return gap;
	}

	/** A fixed width makes a Swing label wrap instead of widening the sidebar. */
	private static String html(String text)
	{
		String escaped = text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
		return "<html><body style='width: 180px'>" + escaped + "</body></html>";
	}
}
