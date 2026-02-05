import { ScrollView, StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';

const entries = [
  { time: '08:30', steps: 1820, source: 'Google Fit' },
  { time: '12:10', steps: 2650, source: 'Google Fit' },
  { time: '15:42', steps: 1980, source: 'Samsung Health' },
  { time: '19:05', steps: 1970, source: 'Samsung Health' },
];

export default function RawHealthScreen() {
  return (
    <ScrollView contentContainerStyle={styles.container}>
      <ThemedView style={styles.summary}>
        <ThemedText type="subtitle">Raw Step Data</ThemedText>
        <ThemedText style={styles.subtle}>Debug view of health records.</ThemedText>
      </ThemedView>
      {entries.map((entry) => (
        <ThemedView key={entry.time} style={styles.card}>
          <View>
            <ThemedText type="defaultSemiBold">{entry.time}</ThemedText>
            <ThemedText style={styles.subtle}>{entry.source}</ThemedText>
          </View>
          <ThemedText type="defaultSemiBold">{entry.steps.toLocaleString()} steps</ThemedText>
        </ThemedView>
      ))}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 20,
    gap: 12,
  },
  summary: {
    borderRadius: 16,
    padding: 14,
    borderWidth: 1,
    borderColor: 'rgba(148,163,184,0.2)',
    gap: 4,
  },
  card: {
    borderRadius: 16,
    padding: 14,
    borderWidth: 1,
    borderColor: 'rgba(148,163,184,0.2)',
    gap: 6,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  subtle: {
    opacity: 0.65,
  },
});
