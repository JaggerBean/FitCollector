import { Modal, Pressable, ScrollView, StyleSheet, Switch, Text, TextInput, View } from 'react-native';
import { useState } from 'react';

const COLORS = {
  bg: '#0e0e0f',
  card: '#232325',
  cardSoft: '#1a1a1c',
  accent: '#7fcd7c',
  text: '#f8fafc',
  textMuted: 'rgba(248,250,252,0.55)',
  border: 'rgba(255,255,255,0.08)',
};

export default function SettingsScreen() {
  const [showNotifications, setShowNotifications] = useState(false);
  const [showMilestones, setShowMilestones] = useState(false);
  const [showAddServer, setShowAddServer] = useState(false);
  const [showPublicServers, setShowPublicServers] = useState(false);
  const [selectedServer, setSelectedServer] = useState('CobbFroge');
  const [milestoneSelection, setMilestoneSelection] = useState<string | null>(null);
  const [adminUpdates, setAdminUpdates] = useState(true);
  const [milestoneAlerts, setMilestoneAlerts] = useState<string[]>([]);

  const milestones = ['Starter · 1000 steps', 'Walker · 5000 steps', 'Legend · 10000 steps'];

  const toggleMilestoneAlert = (value: string) => {
    setMilestoneAlerts((prev) =>
      prev.includes(value) ? prev.filter((item) => item !== value) : [...prev, value]
    );
  };

  return (
    <>
      <ScrollView style={styles.screen} contentContainerStyle={styles.container}>
        <View style={styles.section}>
          <View style={styles.bigButton}>
            <Text style={styles.bigButtonText}>RECENT ACTIVITY LOG</Text>
          </View>
        </View>

      <Text style={styles.sectionTitle}>Appearance</Text>
      <View style={styles.card}>
        <Text style={styles.label}>Theme Mode</Text>
        <View style={styles.segmentRow}>
          <View style={[styles.segment, styles.segmentActive]}>
            <Text style={styles.segmentText}>System</Text>
          </View>
          <View style={styles.segment}>
            <Text style={styles.segmentText}>Light</Text>
          </View>
          <View style={styles.segment}>
            <Text style={styles.segmentText}>Dark</Text>
          </View>
        </View>
      </View>

      <Text style={styles.sectionTitle}>Account Settings</Text>
      <View style={styles.card}>
        <Text style={styles.label}>Minecraft Username</Text>
        <TextInput style={styles.input} value="SoZoCreed" />

        <Text style={[styles.label, styles.labelSpacing]}>Private Server Invite Code</Text>
        <Text style={styles.helper}>Add a private server using its invite code.</Text>
        <TextInput style={styles.input} placeholder="Invite Code" placeholderTextColor={COLORS.textMuted} />

        <View style={styles.rowButtons}>
          <Pressable style={[styles.button, styles.buttonAccent]} onPress={() => setShowAddServer(true)}>
            <Text style={styles.buttonTextDark}>Add Private Server</Text>
          </Pressable>
          <Pressable style={[styles.button, styles.buttonGhost]}>
            <Text style={styles.buttonText}>Scan QR</Text>
          </Pressable>
        </View>

        <Pressable style={styles.infoPill} onPress={() => setShowPublicServers(true)}>
          <Text style={styles.infoPillText}>Servers: 1 selected</Text>
        </Pressable>
        <View style={styles.disabledButton}>
          <Text style={styles.disabledText}>Save & Register All</Text>
        </View>
      </View>

      <Text style={styles.sectionTitle}>Sync & Permissions</Text>
      <View style={styles.card}>
        <View style={styles.toggleRow}>
          <View>
            <Text style={styles.label}>Auto-Sync on Open</Text>
            <Text style={styles.helper}>Sync steps immediately when you open the app.</Text>
          </View>
          <Switch value />
        </View>
        <View style={styles.divider} />
        <View style={styles.toggleRow}>
          <View>
            <Text style={styles.label}>Background Sync</Text>
            <Text style={styles.helper}>Periodic sync every 15 mins while app is closed.</Text>
          </View>
          <Switch value />
        </View>
        <View style={styles.divider} />
        <View>
          <Text style={styles.label}>Sync Frequency</Text>
          <Text style={styles.helper}>Every 15 minutes</Text>
          <View style={styles.sliderTrack}>
            <View style={styles.sliderFill} />
            {[0, 1, 2, 3, 4, 5].map((i) => (
              <View key={i} style={styles.sliderDot} />
            ))}
          </View>
        </View>
        <View style={styles.divider} />
        <View style={styles.toggleRow}>
          <View>
            <Text style={styles.label}>High Reliability Mode</Text>
            <Text style={styles.helper}>Ignore battery optimizations to keep sync running.</Text>
          </View>
          <Switch value />
        </View>
        <View style={styles.healthButton}>
          <Text style={styles.healthButtonText}>Open Health Connect Settings</Text>
        </View>
      </View>

      <Text style={styles.sectionTitle}>Health Connect Settings</Text>
      <View style={styles.card}>
        <Text style={styles.label}>Choose your step source</Text>
        <Text style={styles.helper}>No step sources found in the last 7 days.</Text>
      </View>

      <Text style={styles.sectionTitle}>Notifications</Text>
      <View style={styles.card}>
        <Text style={styles.label}>Enable notifications</Text>
        <Text style={styles.helper}>Get alerts about rewards and server updates.</Text>
        <Pressable style={styles.buttonAccentSoft} onPress={() => setShowNotifications(true)}>
          <Text style={styles.buttonTextDark}>Manage server notifications</Text>
        </Pressable>
      </View>

      <Text style={styles.sectionTitle}>Milestones</Text>
      <View style={styles.card}>
        <Text style={styles.label}>Track milestones</Text>
        <Text style={styles.helper}>Select one milestone per server to track.</Text>
        <Pressable style={styles.buttonAccentSoft} onPress={() => setShowMilestones(true)}>
          <Text style={styles.buttonTextDark}>Track milestones</Text>
        </Pressable>
      </View>
    </ScrollView>

      <Modal transparent animationType="fade" visible={showAddServer} onRequestClose={() => setShowAddServer(false)}>
        <Pressable style={styles.modalOverlay} onPress={() => setShowAddServer(false)}>
          <Pressable style={styles.modalCard} onPress={(event) => event.stopPropagation()}>
            <Text style={styles.modalTitle}>Add private server</Text>
            <TextInput
              style={styles.modalInput}
              placeholder="Invite Code"
              placeholderTextColor={COLORS.textMuted}
            />
            <View style={styles.modalButtonsRow}>
              <Pressable style={[styles.button, styles.buttonGhost]}> 
                <Text style={styles.buttonText}>Scan QR</Text>
              </Pressable>
              <Pressable style={[styles.button, styles.buttonAccent]}>
                <Text style={styles.buttonTextDark}>Add</Text>
              </Pressable>
            </View>
            <Pressable onPress={() => setShowAddServer(false)}>
              <Text style={styles.modalLink}>Close</Text>
            </Pressable>
          </Pressable>
        </Pressable>
      </Modal>

      <Modal
        transparent
        animationType="fade"
        visible={showPublicServers}
        onRequestClose={() => setShowPublicServers(false)}
      >
        <Pressable style={styles.modalOverlay} onPress={() => setShowPublicServers(false)}>
          <Pressable style={styles.sheetCard} onPress={(event) => event.stopPropagation()}>
            <Text style={styles.modalTitle}>Public servers</Text>
            <View style={styles.searchInput}>
              <View style={styles.searchIcon} />
              <Text style={styles.searchPlaceholder}>Search Servers</Text>
            </View>
            <Pressable style={styles.serverRow} onPress={() => setSelectedServer('CobbFroge')}> 
              <View style={[styles.checkbox, selectedServer === 'CobbFroge' && styles.checkboxActive]} />
              <Text style={styles.serverName}>CobbFrogeserver</Text>
            </Pressable>
            <View style={styles.sheetFooter}>
              <Pressable style={styles.doneButton} onPress={() => setShowPublicServers(false)}>
                <Text style={styles.doneButtonText}>Done</Text>
              </Pressable>
            </View>
          </Pressable>
        </Pressable>
      </Modal>

      <Modal
        transparent
        animationType="fade"
        visible={showNotifications}
        onRequestClose={() => setShowNotifications(false)}
      >
        <Pressable style={styles.modalOverlay} onPress={() => setShowNotifications(false)}>
          <Pressable style={styles.modalCardLarge} onPress={(event) => event.stopPropagation()}>
            <Text style={styles.modalTitle}>Server notifications</Text>
            <Text style={styles.modalSubtitle}>Select a server to manage admin updates and milestone alerts.</Text>

            <Pressable style={styles.radioRow} onPress={() => setSelectedServer('CobbFroge')}>
              <View style={[styles.radioOuter, selectedServer === 'CobbFroge' && styles.radioOuterActive]}>
                {selectedServer === 'CobbFroge' && <View style={styles.radioInner} />}
              </View>
              <Text style={styles.serverName}>CobbFroge</Text>
            </Pressable>

            <View style={styles.toggleRow}>
              <Text style={styles.label}>Admin updates</Text>
              <Switch value={adminUpdates} onValueChange={setAdminUpdates} />
            </View>

            <Text style={styles.modalSectionTitle}>Milestone alerts</Text>
            {milestones.map((item) => (
              <Pressable key={item} style={styles.checkRow} onPress={() => toggleMilestoneAlert(item)}>
                <View style={[styles.checkbox, milestoneAlerts.includes(item) && styles.checkboxActive]} />
                <Text style={styles.modalItemText}>{item}</Text>
              </Pressable>
            ))}

            <Pressable style={styles.doneButton} onPress={() => setShowNotifications(false)}>
              <Text style={styles.doneButtonText}>Done</Text>
            </Pressable>
          </Pressable>
        </Pressable>
      </Modal>

      <Modal transparent animationType="fade" visible={showMilestones} onRequestClose={() => setShowMilestones(false)}>
        <Pressable style={styles.modalOverlay} onPress={() => setShowMilestones(false)}>
          <Pressable style={styles.modalCardLarge} onPress={(event) => event.stopPropagation()}>
            <Text style={styles.modalTitle}>Track milestones</Text>
            <Text style={styles.modalSubtitle}>Select a server, then choose one milestone to track.</Text>

            <Pressable style={styles.radioRow} onPress={() => setSelectedServer('CobbFroge')}>
              <View style={[styles.radioOuter, selectedServer === 'CobbFroge' && styles.radioOuterActive]}>
                {selectedServer === 'CobbFroge' && <View style={styles.radioInner} />}
              </View>
              <Text style={styles.serverName}>CobbFroge</Text>
            </Pressable>

            <Text style={styles.modalSectionTitle}>Milestones</Text>
            {milestones.map((item) => (
              <Pressable key={item} style={styles.radioRow} onPress={() => setMilestoneSelection(item)}>
                <View style={[styles.radioOuter, milestoneSelection === item && styles.radioOuterActive]}>
                  {milestoneSelection === item && <View style={styles.radioInner} />}
                </View>
                <Text style={styles.modalItemText}>{item}</Text>
              </Pressable>
            ))}

            <Pressable onPress={() => setMilestoneSelection(null)}>
              <Text style={styles.modalLink}>Clear selection</Text>
            </Pressable>

            <Pressable style={styles.doneButton} onPress={() => setShowMilestones(false)}>
              <Text style={styles.doneButtonText}>Done</Text>
            </Pressable>
          </Pressable>
        </Pressable>
      </Modal>
    </>
  );
}

const styles = StyleSheet.create({
  screen: {
    backgroundColor: COLORS.bg,
  },
  container: {
    padding: 16,
    paddingBottom: 30,
    gap: 14,
  },
  section: {
    marginTop: 6,
  },
  sectionTitle: {
    color: COLORS.text,
    fontSize: 14,
    fontWeight: '700',
    marginTop: 6,
  },
  card: {
    backgroundColor: COLORS.card,
    borderRadius: 16,
    padding: 14,
    gap: 10,
    borderWidth: 1,
    borderColor: COLORS.border,
  },
  bigButton: {
    backgroundColor: '#67b7f7',
    borderRadius: 12,
    paddingVertical: 12,
    alignItems: 'center',
  },
  bigButtonText: {
    color: '#0d1b2a',
    fontWeight: '700',
  },
  label: {
    color: COLORS.text,
    fontWeight: '600',
  },
  labelSpacing: {
    marginTop: 8,
  },
  helper: {
    color: COLORS.textMuted,
    fontSize: 11,
  },
  input: {
    borderWidth: 1,
    borderColor: COLORS.border,
    borderRadius: 10,
    paddingHorizontal: 12,
    paddingVertical: 8,
    color: COLORS.text,
    backgroundColor: COLORS.cardSoft,
  },
  segmentRow: {
    flexDirection: 'row',
    gap: 8,
  },
  segment: {
    flex: 1,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: COLORS.border,
    paddingVertical: 8,
    alignItems: 'center',
  },
  segmentActive: {
    backgroundColor: '#3a3a40',
  },
  segmentText: {
    color: COLORS.text,
    fontSize: 12,
  },
  rowButtons: {
    flexDirection: 'row',
    gap: 10,
    marginTop: 6,
  },
  button: {
    flex: 1,
    paddingVertical: 10,
    borderRadius: 999,
    alignItems: 'center',
  },
  buttonAccent: {
    backgroundColor: COLORS.accent,
  },
  buttonGhost: {
    borderWidth: 1,
    borderColor: COLORS.accent,
  },
  buttonText: {
    color: COLORS.accent,
    fontWeight: '600',
  },
  buttonTextDark: {
    color: '#163319',
    fontWeight: '700',
  },
  infoPill: {
    marginTop: 6,
    borderWidth: 1,
    borderColor: COLORS.border,
    borderRadius: 999,
    paddingVertical: 8,
    alignItems: 'center',
  },
  infoPillText: {
    color: COLORS.accent,
    fontWeight: '600',
  },
  disabledButton: {
    marginTop: 6,
    borderRadius: 999,
    paddingVertical: 10,
    alignItems: 'center',
    backgroundColor: '#2a2a2d',
  },
  disabledText: {
    color: COLORS.textMuted,
  },
  toggleRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    gap: 10,
  },
  divider: {
    height: 1,
    backgroundColor: COLORS.border,
  },
  sliderTrack: {
    marginTop: 6,
    height: 10,
    borderRadius: 999,
    backgroundColor: '#35353a',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 8,
  },
  sliderFill: {
    position: 'absolute',
    left: 0,
    top: 0,
    bottom: 0,
    width: '35%',
    borderRadius: 999,
    backgroundColor: COLORS.accent,
  },
  sliderDot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    backgroundColor: '#7c7c85',
  },
  healthButton: {
    backgroundColor: '#74bdfb',
    borderRadius: 12,
    paddingVertical: 10,
    alignItems: 'center',
  },
  healthButtonText: {
    color: '#0b2440',
    fontWeight: '700',
  },
  buttonAccentSoft: {
    marginTop: 6,
    backgroundColor: '#81c97f',
    borderRadius: 999,
    paddingVertical: 10,
    alignItems: 'center',
  },
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.6)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 18,
  },
  modalCard: {
    width: '100%',
    backgroundColor: '#262629',
    borderRadius: 18,
    padding: 16,
    gap: 12,
  },
  modalCardLarge: {
    width: '100%',
    backgroundColor: '#262629',
    borderRadius: 18,
    padding: 16,
    gap: 12,
  },
  modalTitle: {
    color: COLORS.text,
    fontSize: 16,
    fontWeight: '700',
  },
  modalSubtitle: {
    color: COLORS.textMuted,
    fontSize: 12,
  },
  modalInput: {
    borderWidth: 1,
    borderColor: COLORS.border,
    borderRadius: 10,
    paddingHorizontal: 12,
    paddingVertical: 10,
    color: COLORS.text,
    backgroundColor: COLORS.cardSoft,
  },
  modalButtonsRow: {
    flexDirection: 'row',
    gap: 10,
  },
  modalLink: {
    color: COLORS.accent,
    textAlign: 'right',
    fontWeight: '600',
  },
  sheetCard: {
    width: '100%',
    backgroundColor: '#262629',
    borderRadius: 18,
    padding: 16,
    gap: 12,
  },
  searchInput: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    borderWidth: 1,
    borderColor: COLORS.border,
    borderRadius: 12,
    paddingHorizontal: 12,
    paddingVertical: 10,
    backgroundColor: '#1b1b1d',
  },
  searchIcon: {
    width: 14,
    height: 14,
    borderRadius: 7,
    borderWidth: 1,
    borderColor: COLORS.textMuted,
  },
  searchPlaceholder: {
    color: COLORS.textMuted,
    fontSize: 12,
  },
  serverRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    paddingVertical: 10,
  },
  serverName: {
    color: COLORS.text,
    fontWeight: '600',
  },
  sheetFooter: {
    alignItems: 'flex-end',
  },
  doneButton: {
    marginTop: 10,
    alignSelf: 'flex-end',
    backgroundColor: '#81c97f',
    borderRadius: 999,
    paddingVertical: 8,
    paddingHorizontal: 18,
  },
  doneButtonText: {
    color: '#15311a',
    fontWeight: '700',
  },
  radioRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  radioOuter: {
    width: 18,
    height: 18,
    borderRadius: 9,
    borderWidth: 2,
    borderColor: COLORS.textMuted,
    alignItems: 'center',
    justifyContent: 'center',
  },
  radioOuterActive: {
    borderColor: COLORS.accent,
  },
  radioInner: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: COLORS.accent,
  },
  modalSectionTitle: {
    color: COLORS.text,
    fontWeight: '700',
    marginTop: 6,
  },
  checkRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  checkbox: {
    width: 16,
    height: 16,
    borderRadius: 4,
    borderWidth: 1,
    borderColor: COLORS.textMuted,
  },
  checkboxActive: {
    backgroundColor: COLORS.accent,
    borderColor: COLORS.accent,
  },
  modalItemText: {
    color: COLORS.text,
    fontSize: 12,
  },
});
