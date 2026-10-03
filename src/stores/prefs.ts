import AsyncStorage from '@react-native-async-storage/async-storage';
import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';

export type Appearance = 'system' | 'light' | 'dark';
export type DisappearingDefault = 'off' | '24h' | '7d';

type PrefsState = {
  hydrated: boolean;
  appearance: Appearance;
  /** The intro slides have been completed or skipped at least once on this device. */
  onboardingSeen: boolean;
  /** The permission primer has been shown (each permission may still be denied). */
  permissionsPrimed: boolean;
  /** Hide app content in the recent-apps switcher (Android FLAG_SECURE). */
  hideInSwitcher: boolean;
  /** Timer applied to new chats you create. */
  disappearingDefault: DisappearingDefault;
  setAppearance: (a: Appearance) => void;
  setOnboardingSeen: (v: boolean) => void;
  setPermissionsPrimed: (v: boolean) => void;
  setHideInSwitcher: (v: boolean) => void;
  setDisappearingDefault: (v: DisappearingDefault) => void;
  /** Called on sign-out: keeps device-level choices (appearance) but resets the per-account flow. */
  resetForSignOut: () => void;
};

/**
 * Non-sensitive device preferences. Stored in AsyncStorage; never synced to Firebase,
 * so changing them costs no Firestore writes.
 */
export const usePrefs = create<PrefsState>()(
  persist(
    (set) => ({
      hydrated: false,
      appearance: 'dark',
      onboardingSeen: false,
      permissionsPrimed: false,
      hideInSwitcher: true,
      disappearingDefault: 'off',
      setAppearance: (appearance) => set({ appearance }),
      setOnboardingSeen: (onboardingSeen) => set({ onboardingSeen }),
      setPermissionsPrimed: (permissionsPrimed) => set({ permissionsPrimed }),
      setHideInSwitcher: (hideInSwitcher) => set({ hideInSwitcher }),
      setDisappearingDefault: (disappearingDefault) => set({ disappearingDefault }),
      resetForSignOut: () => set({ permissionsPrimed: false }),
    }),
    {
      name: 'nook.prefs.v1',
      storage: createJSONStorage(() => AsyncStorage),
      partialize: ({ appearance, onboardingSeen, permissionsPrimed, hideInSwitcher, disappearingDefault }) => ({
        appearance,
        onboardingSeen,
        permissionsPrimed,
        hideInSwitcher,
        disappearingDefault,
      }),
      onRehydrateStorage: () => () => {
        usePrefs.setState({ hydrated: true });
      },
    },
  ),
);
