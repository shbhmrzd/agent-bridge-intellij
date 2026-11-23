package dev.agentbridge;

import javax.swing.*;
import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Plain Swing content shared by the IDE dialog and rendering tests. */
final class SavedChatsPanel extends JPanel {
    final DefaultListModel<ConversationStore.Summary> entries = new DefaultListModel<>();
    final JList<ConversationStore.Summary> list = new JList<>(entries);
    final JButton rename = new JButton("Rename…"), delete = new JButton("Delete…");
    final JLabel status = new JLabel("Loading saved conversations…");
    SavedChatsPanel() {
        super(new BorderLayout(8, 8)); setPreferredSize(new Dimension(520, 340));
        add(new JLabel("Saved on this device for this project. Select a conversation to resume."), BorderLayout.NORTH);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.getAccessibleContext().setAccessibleName("Saved conversations");
        DateTimeFormatter date = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(ZoneId.systemDefault());
        list.setCellRenderer((items, entry, index, selected, focus) -> {
            JLabel label = new JLabel(entry.title()); label.putClientProperty("html.disable", true);
            label.setOpaque(true); label.setBorder(BorderFactory.createEmptyBorder(9, 8, 9, 8));
            label.setBackground(selected ? items.getSelectionBackground() : items.getBackground());
            label.setForeground(selected ? items.getSelectionForeground() : items.getForeground());
            label.setToolTipText(entry.readable() ? entry.title() + " · " + date.format(Instant.ofEpochMilli(entry.updated()))
                : "This file could not be read. You can delete it or recover it from a backup.");
            return label;
        });
        add(new JScrollPane(list), BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(0, 6));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0)); actions.add(rename); actions.add(delete);
        bottom.add(actions, BorderLayout.NORTH); status.putClientProperty("html.disable", true); bottom.add(status, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH); enabled(false);
    }
    void entries(List<ConversationStore.Summary> values, String selected) {
        entries.clear(); values.forEach(entries::addElement);
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).id().equals(selected)) list.setSelectedIndex(i);
        if (list.isSelectionEmpty() && !entries.isEmpty()) list.setSelectedIndex(0);
        status.setText(values.isEmpty() ? "No saved conversations yet. Send a message to start one." : values.size() + " saved conversations");
    }
    void enabled(boolean enabled) {
        list.setEnabled(enabled); var selected = list.getSelectedValue();
        rename.setEnabled(enabled && selected != null && selected.readable()); delete.setEnabled(enabled && selected != null);
    }
}
