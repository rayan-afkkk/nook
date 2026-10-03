import { createContext, useContext, useMemo, type PropsWithChildren } from 'react';
import { useColorScheme } from 'react-native';

import { usePrefs } from '@/stores/prefs';

import { darkColors, lightColors, type ColorScheme, type ThemeColors } from './tokens';

export type Theme = {
  scheme: ColorScheme;
  colors: ThemeColors;
};

const ThemeContext = createContext<Theme>({ scheme: 'dark', colors: darkColors });

/** Resolves the Appearance preference (System / Light / Dark) into concrete colours. */
export function ThemeProvider({ children }: PropsWithChildren) {
  const appearance = usePrefs((s) => s.appearance);
  const system = useColorScheme();
  const scheme: ColorScheme =
    appearance === 'system' ? (system === 'light' ? 'light' : 'dark') : appearance;

  const value = useMemo<Theme>(
    () => ({ scheme, colors: scheme === 'dark' ? darkColors : lightColors }),
    [scheme],
  );
  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme(): Theme {
  return useContext(ThemeContext);
}
