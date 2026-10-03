import * as ScreenCapture from 'expo-screen-capture';
import { useEffect } from 'react';

import { usePrefs } from '@/stores/prefs';

const KEY = 'nook-privacy';

/**
 * Android: sets FLAG_SECURE so the recent-apps preview is blank. A side effect of
 * FLAG_SECURE is that screenshots and screen recording of NOOK are blocked too;
 * the toggle in Account > App lock explains this.
 */
export function PrivacyGuard({ active }: { active: boolean }) {
  const hide = usePrefs((s) => s.hideInSwitcher);
  const on = active && hide;
  useEffect(() => {
    if (on) {
      ScreenCapture.preventScreenCaptureAsync(KEY).catch(() => undefined);
      return () => {
        ScreenCapture.allowScreenCaptureAsync(KEY).catch(() => undefined);
      };
    }
    return undefined;
  }, [on]);
  return null;
}
