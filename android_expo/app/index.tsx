import { Pressable, RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useCallback, useState } from 'react';
import { useRouter } from 'expo-router';

const COLORS = {
  bg: '#0e0e0f',
  card: '#232325',
  cardSoft: '#1b1b1d',
  accent: '#7fcd7c',
  accentDark: '#2a7b2f',
  accentSoft: '#aee3a7',
  text: '#f8fafc',
  textMuted: 'rgba(248,250,252,0.6)',
  border: 'rgba(255,255,255,0.06)',
  banner: '#f3e6a6',
  bannerText: '#2b2b2b',
};

export default function DashboardScreen() {
  const router = useRouter();
  const [refreshing, setRefreshing] = useState(false);

  const onRefresh = useCallback(() => {
    setRefreshing(true);
    setTimeout(() => setRefreshing(false), 800);
  }, []);

  return (
    <ScrollView
      style={styles.screen}
      contentContainerStyle={styles.container}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} />}
    >
      <View style={styles.header}>
        <View style={styles.headerLeft}>
          <View style={styles.avatar} />
          <View style={styles.serverIcon} />
        </View>
        <Text style={styles.headerTitle}>StepCraft</Text>
        <Pressable style={styles.headerRight} onPress={() => router.push('/settings')}>
          <View style={styles.gear} />
        </Pressable>
      </View>

      <View style={styles.resetBanner}>
        <Text style={styles.resetText}>Time Until Daily Step Reset: 15:23:43</Text>
      </View>

      <View style={styles.rewardBanner}>
        <View style={styles.rewardIcon} />
        <View style={styles.rewardBody}>
          <Text style={styles.rewardTitle}>Unclaimed rewards:</Text>
          <Text style={styles.rewardText}>• CobbFroge: 1 unclaimed (Starter)</Text>
          <Text style={styles.rewardText}>Join the server to claim rewards!</Text>
        </View>
      </View>

      <View style={styles.stepsCard}>
        <View style={styles.stepsHeader}>
          <View style={styles.walkIcon} />
          <Text style={styles.stepsTitle}>Steps Today</Text>
        </View>
        <Text style={styles.stepsValue}>0</Text>
        <Text style={styles.stepsSub}>Come on, you can do it!</Text>
        <View style={styles.syncButton}>
          <Text style={styles.syncText}>SYNC NOW</Text>
        </View>
      </View>

      <View style={styles.syncStatus}>
        <View style={styles.syncCheck} />
        <View style={styles.syncCopy}>
          <Text style={styles.syncTitle}>Synced to CobbFroge</Text>
          <Text style={styles.syncSub}>20 minutes ago</Text>
        </View>
      </View>

      <Text style={styles.footerText}>Auto-sync & Background-sync enabled (1 servers).</Text>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  screen: {
    backgroundColor: COLORS.bg,
  },
  container: {
    padding: 16,
    paddingBottom: 30,
    gap: 12,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginTop: 8,
    marginBottom: 4,
  },
  headerLeft: {
    flexDirection: 'row',
    gap: 10,
    alignItems: 'center',
  },
  avatar: {
    width: 24,
    height: 24,
    borderRadius: 6,
    backgroundColor: '#ffcc66',
  },
  serverIcon: {
    width: 24,
    height: 24,
    borderRadius: 6,
    backgroundColor: '#2ecc71',
  },
  headerTitle: {
    color: COLORS.accent,
    fontSize: 20,
    fontWeight: '700',
  },
  headerRight: {
    width: 24,
    alignItems: 'flex-end',
  },
  gear: {
    width: 18,
    height: 18,
    borderRadius: 9,
    borderWidth: 2,
    borderColor: COLORS.textMuted,
  },
  resetBanner: {
    backgroundColor: '#2a2a2f',
    borderRadius: 12,
    paddingVertical: 8,
    alignItems: 'center',
  },
  resetText: {
    color: '#7cc2ff',
    fontWeight: '600',
    fontSize: 12,
  },
  rewardBanner: {
    backgroundColor: COLORS.banner,
    borderRadius: 12,
    padding: 12,
    flexDirection: 'row',
    gap: 10,
  },
  rewardIcon: {
    width: 26,
    height: 26,
    borderRadius: 8,
    backgroundColor: '#e0b85a',
    marginTop: 2,
  },
  rewardBody: {
    flex: 1,
    gap: 2,
  },
  rewardTitle: {
    color: COLORS.bannerText,
    fontWeight: '700',
    fontSize: 12,
  },
  rewardText: {
    color: COLORS.bannerText,
    fontSize: 11,
  },
  stepsCard: {
    backgroundColor: '#2d7a30',
    borderRadius: 18,
    padding: 18,
    gap: 12,
  },
  stepsHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    justifyContent: 'center',
  },
  walkIcon: {
    width: 18,
    height: 18,
    borderRadius: 9,
    borderWidth: 2,
    borderColor: '#e6ffe6',
  },
  stepsTitle: {
    color: '#e6ffe6',
    fontWeight: '700',
  },
  stepsValue: {
    color: '#ffffff',
    fontSize: 52,
    textAlign: 'center',
    fontWeight: '700',
  },
  stepsSub: {
    color: '#d6f5d6',
    textAlign: 'center',
    fontSize: 12,
  },
  syncButton: {
    marginTop: 8,
    backgroundColor: '#f5f5f5',
    paddingVertical: 12,
    borderRadius: 999,
    alignItems: 'center',
  },
  syncText: {
    color: '#1f5b2a',
    fontWeight: '700',
    letterSpacing: 1,
  },
  syncStatus: {
    backgroundColor: '#2f7f33',
    borderRadius: 14,
    padding: 12,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  syncCheck: {
    width: 18,
    height: 18,
    borderRadius: 9,
    borderWidth: 2,
    borderColor: '#dff5d9',
  },
  syncCopy: {
    gap: 2,
  },
  syncTitle: {
    color: '#e6ffe6',
    fontWeight: '700',
    fontSize: 12,
  },
  syncSub: {
    color: '#cce7cc',
    fontSize: 11,
  },
  footerText: {
    color: COLORS.textMuted,
    fontSize: 11,
    textAlign: 'center',
    marginTop: 4,
  },
});
