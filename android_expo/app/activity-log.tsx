import { ScrollView, StyleSheet, Text, View } from 'react-native';

const COLORS = {
  bg: '#0e0e0f',
  card: '#1f1f22',
  text: '#f8fafc',
  textMuted: 'rgba(248,250,252,0.55)',
  border: 'rgba(255,255,255,0.06)',
  pill: '#3b3b4a',
  green: '#7fcd7c',
};

const logItems = [
  { time: '2026-01-30 10:58:40', label: 'Synced to CobbFroge', tag: 'Background' },
  { time: '2026-01-30 10:43:39', label: 'Synced to CobbFroge', tag: 'Background' },
  { time: '2026-01-30 10:28:39', label: 'Synced to CobbFroge', tag: 'Background' },
  { time: '2026-01-30 10:13:39', label: 'Synced to CobbFroge', tag: 'Background' },
  { time: '2026-01-30 09:58:38', label: 'Synced to CobbFroge', tag: 'Background' },
  { time: '2026-01-30 09:43:38', label: 'Synced to CobbFroge', tag: 'Background' },
  { time: '2026-01-30 09:28:37', label: 'Synced to CobbFroge', tag: 'Background' },
  { time: '2026-01-30 09:15:43', label: 'Synced to CobbFroge', tag: 'Auto' },
];

export default function ActivityLogScreen() {
  return (
    <ScrollView style={styles.screen} contentContainerStyle={styles.container}>
      {logItems.map((item) => (
        <View key={item.time} style={styles.card}>
          <View style={styles.dot} />
          <View style={styles.cardBody}>
            <Text style={styles.title}>{item.label}</Text>
            <Text style={styles.time}>{item.time}</Text>
          </View>
          <View style={styles.tag}>
            <Text style={styles.tagText}>{item.tag}</Text>
          </View>
        </View>
      ))}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  screen: {
    backgroundColor: COLORS.bg,
  },
  container: {
    padding: 14,
    gap: 10,
  },
  card: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    padding: 12,
    borderRadius: 14,
    backgroundColor: COLORS.card,
    borderWidth: 1,
    borderColor: COLORS.border,
  },
  dot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: COLORS.green,
  },
  cardBody: {
    flex: 1,
    gap: 2,
  },
  title: {
    color: COLORS.text,
    fontWeight: '700',
    fontSize: 12,
  },
  time: {
    color: COLORS.textMuted,
    fontSize: 11,
  },
  tag: {
    backgroundColor: COLORS.pill,
    borderRadius: 999,
    paddingHorizontal: 10,
    paddingVertical: 4,
  },
  tagText: {
    color: COLORS.text,
    fontSize: 10,
  },
});
