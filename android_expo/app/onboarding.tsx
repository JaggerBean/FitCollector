import { useMemo, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { useRouter } from 'expo-router';

const COLORS = {
  bg: '#0e0e0f',
  card: '#232325',
  accent: '#7fcd7c',
  text: '#f8fafc',
  textMuted: 'rgba(248,250,252,0.6)',
  border: 'rgba(255,255,255,0.08)',
};

const steps = [
  {
    id: 1,
    title: 'Step 1: Health Permissions',
    description: 'Allow StepCraft to read your daily step count from Health Connect.',
    action: 'Authorize Health Connect',
    icon: 'heart',
  },
  {
    id: 4,
    title: 'Step 4: Minecraft Username',
    description: 'Enter your exact Minecraft username.',
    action: 'Next',
    icon: 'user',
  },
  {
    id: 5,
    title: 'Step 5: Confirm Username',
    description: 'Is this the correct Minecraft username?',
    action: 'Confirm',
    icon: 'avatar',
  },
  {
    id: 6,
    title: 'Step 6: Select Servers',
    description: 'Choose which servers to sync with.',
    action: 'Complete setup',
    icon: 'server',
  },
];

export default function OnboardingScreen() {
  const router = useRouter();
  const [active, setActive] = useState(6);
  const step = useMemo(() => steps.find((s) => s.id === active) ?? steps[0], [active]);
  const isLastStep = active === 6;

  const handlePrimary = () => {
    if (isLastStep) {
      router.replace('/');
      return;
    }
    const currentIndex = steps.findIndex((s) => s.id === active);
    const nextStep = steps[currentIndex + 1];
    if (nextStep) {
      setActive(nextStep.id);
    }
  };

  return (
    <ScrollView style={styles.screen} contentContainerStyle={styles.container}>
      <Text style={styles.title}>Welcome to StepCraft</Text>
      <Text style={styles.subtitle}>Complete these steps to start earning rewards.</Text>

      <View style={styles.progressRow}>
        {Array.from({ length: 6 }).map((_, index) => {
          const i = index + 1;
          const isOn = i <= active;
          return <View key={i} style={[styles.progressBar, isOn && styles.progressBarActive]} />;
        })}
      </View>

      <View style={styles.iconWrap}>
        <View style={styles.iconCircle} />
      </View>

      <Text style={styles.stepTitle}>{step.title}</Text>
      <Text style={styles.stepDesc}>{step.description}</Text>

      {active === 4 && (
        <TextInput
          style={styles.input}
          placeholder="Minecraft Username"
          placeholderTextColor={COLORS.textMuted}
        />
      )}

      {active === 5 && (
        <View style={styles.avatarCard}>
          <View style={styles.avatarLarge} />
          <Text style={styles.avatarName}>SoZoCreed</Text>
          <View style={styles.confirmRow}>
            <View style={styles.buttonGhost}>
              <Text style={styles.buttonGhostText}>Back</Text>
            </View>
            <View style={styles.buttonAccent}>
              <Text style={styles.buttonAccentText}>Confirm</Text>
            </View>
          </View>
        </View>
      )}

      {active === 6 && (
        <View style={styles.serverCard}>
          <View style={styles.serverIcon} />
          <Text style={styles.serverLabel}>Step 6: Select Servers</Text>
          <Text style={styles.serverDesc}>Choose which servers to sync with.</Text>
          <View style={styles.serverButtons}>
            <View style={styles.buttonGhost}>
              <Text style={styles.buttonGhostText}>Browse public</Text>
            </View>
            <View style={styles.buttonGhost}>
              <Text style={styles.buttonGhostText}>Add private</Text>
            </View>
          </View>
          <Text style={styles.serverHint}>No servers selected.</Text>
        </View>
      )}

      <Pressable style={styles.primaryButton} onPress={handlePrimary}>
        <Text style={styles.primaryButtonText}>{step.action}</Text>
      </Pressable>

      <Pressable style={styles.skipButton} onPress={() => router.replace('/')}
        accessibilityRole="button"
      >
        <Text style={styles.skipText}>Skip for now</Text>
      </Pressable>

      <View style={styles.stepSwitcher}>
        {steps.map((s) => (
          <View
            key={s.id}
            style={[styles.stepChip, s.id === active && styles.stepChipActive]}
            onTouchEnd={() => setActive(s.id)}
          >
            <Text style={[styles.stepChipText, s.id === active && styles.stepChipTextActive]}>
              {s.id}
            </Text>
          </View>
        ))}
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  screen: {
    backgroundColor: COLORS.bg,
  },
  container: {
    padding: 20,
    alignItems: 'center',
    gap: 10,
  },
  title: {
    color: COLORS.accent,
    fontSize: 20,
    fontWeight: '700',
    marginTop: 18,
  },
  subtitle: {
    color: COLORS.textMuted,
    textAlign: 'center',
  },
  progressRow: {
    flexDirection: 'row',
    gap: 6,
    marginVertical: 8,
  },
  progressBar: {
    width: 28,
    height: 4,
    borderRadius: 999,
    backgroundColor: '#3a3a3f',
  },
  progressBarActive: {
    backgroundColor: COLORS.accent,
  },
  iconWrap: {
    marginTop: 10,
    marginBottom: 10,
  },
  iconCircle: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: COLORS.accent,
  },
  stepTitle: {
    color: COLORS.text,
    fontSize: 18,
    fontWeight: '700',
    textAlign: 'center',
  },
  stepDesc: {
    color: COLORS.textMuted,
    textAlign: 'center',
    maxWidth: 260,
  },
  input: {
    width: '100%',
    borderWidth: 1,
    borderColor: COLORS.border,
    borderRadius: 10,
    paddingHorizontal: 12,
    paddingVertical: 10,
    color: COLORS.text,
    marginTop: 16,
    backgroundColor: '#1b1b1d',
  },
  avatarCard: {
    marginTop: 16,
    alignItems: 'center',
    gap: 12,
  },
  avatarLarge: {
    width: 80,
    height: 80,
    borderRadius: 12,
    backgroundColor: '#f2c94c',
  },
  avatarName: {
    color: COLORS.text,
    fontSize: 16,
    fontWeight: '700',
  },
  confirmRow: {
    flexDirection: 'row',
    gap: 12,
  },
  serverCard: {
    marginTop: 16,
    alignItems: 'center',
    gap: 10,
  },
  serverIcon: {
    width: 32,
    height: 32,
    borderRadius: 10,
    backgroundColor: COLORS.accent,
  },
  serverLabel: {
    color: COLORS.text,
    fontWeight: '700',
  },
  serverDesc: {
    color: COLORS.textMuted,
  },
  serverButtons: {
    flexDirection: 'row',
    gap: 10,
    marginTop: 6,
  },
  serverHint: {
    color: COLORS.textMuted,
    fontSize: 12,
  },
  primaryButton: {
    backgroundColor: COLORS.accent,
    borderRadius: 999,
    paddingVertical: 12,
    paddingHorizontal: 28,
    marginTop: 14,
    alignItems: 'center',
    width: '100%',
  },
  primaryButtonText: {
    color: '#14341b',
    fontWeight: '700',
  },
  buttonGhost: {
    borderWidth: 1,
    borderColor: COLORS.accent,
    borderRadius: 999,
    paddingHorizontal: 18,
    paddingVertical: 8,
  },
  buttonGhostText: {
    color: COLORS.accent,
    fontWeight: '600',
  },
  buttonAccent: {
    backgroundColor: COLORS.accent,
    borderRadius: 999,
    paddingHorizontal: 18,
    paddingVertical: 8,
  },
  buttonAccentText: {
    color: '#14341b',
    fontWeight: '700',
  },
  stepSwitcher: {
    flexDirection: 'row',
    gap: 8,
    marginTop: 20,
    marginBottom: 10,
  },
  skipButton: {
    marginTop: 6,
  },
  skipText: {
    color: COLORS.textMuted,
    fontSize: 12,
    textDecorationLine: 'underline',
  },
  stepChip: {
    width: 28,
    height: 28,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: COLORS.border,
    alignItems: 'center',
    justifyContent: 'center',
  },
  stepChipActive: {
    backgroundColor: COLORS.accent,
    borderColor: COLORS.accent,
  },
  stepChipText: {
    color: COLORS.textMuted,
    fontSize: 12,
  },
  stepChipTextActive: {
    color: '#14341b',
    fontWeight: '700',
  },
});
