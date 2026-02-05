import { DarkTheme, DefaultTheme, ThemeProvider } from '@react-navigation/native';
import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import 'react-native-reanimated';

import { useColorScheme } from '@/hooks/use-color-scheme';

export default function RootLayout() {
  const colorScheme = useColorScheme();

  return (
    <ThemeProvider value={colorScheme === 'dark' ? DarkTheme : DefaultTheme}>
      <Stack
        screenOptions={{
          headerStyle: { backgroundColor: colorScheme === 'dark' ? '#0b1220' : '#ffffff' },
          headerTintColor: colorScheme === 'dark' ? '#f8fafc' : '#0f172a',
        }}
      >
        <Stack.Screen name="onboarding" options={{ title: 'StepCraft' }} />
        <Stack.Screen name="index" options={{ title: 'Dashboard' }} />
        <Stack.Screen name="settings" options={{ title: 'Settings' }} />
        <Stack.Screen name="activity-log" options={{ title: 'Activity Log' }} />
        <Stack.Screen name="raw-health" options={{ title: 'Raw Health Data' }} />
      </Stack>
      <StatusBar style={colorScheme === 'dark' ? 'light' : 'dark'} />
    </ThemeProvider>
  );
}
