/**
 * NOOK design tokens. Every colour, size and duration used by the UI lives here,
 * so screens never hard-code values.
 */

export const palette = {
  black: '#000000',
  charcoal: '#1E1B18',
  charcoalRaised: '#2A2622',
  border: '#2E2A26',
  cream: '#F5EFE6',
  creamBg: '#F7F2EA',
  creamSurface: '#FFFFFF',
  creamRaised: '#EDE5D8',
  creamBorder: '#E3DACB',
  muted: '#9A938A',
  mutedLight: '#7A7268',
  orange: '#FF6A33',
  amber: '#E8B04B',
  coral: '#F26B5E',
  mint: '#CDEFD9',
  sky: '#D4E6FB',
  peach: '#FBE3C0',
  lavender: '#DDD0F5',
  rose: '#F6C9C4',
  navy: '#1F3A52',
  online: '#5BD68A',
} as const;

export type ColorScheme = 'dark' | 'light';

export type ThemeColors = {
  background: string;
  surface: string;
  surfaceRaised: string;
  border: string;
  text: string;
  textMuted: string;
  /** Colour of text drawn on top of `primary` (cream pill buttons, my bubbles). */
  onPrimary: string;
  primary: string;
  accent: string;
  info: string;
  danger: string;
  highlight: string;
  onHighlight: string;
  online: string;
  overlay: string;
  pastel: { mint: string; sky: string; peach: string; lavender: string; rose: string };
  /** Text colour used on pastel tiles in both themes. */
  onPastel: string;
};

const pastel = {
  mint: palette.mint,
  sky: palette.sky,
  peach: palette.peach,
  lavender: palette.lavender,
  rose: palette.rose,
};

export const darkColors: ThemeColors = {
  background: palette.black,
  surface: palette.charcoal,
  surfaceRaised: palette.charcoalRaised,
  border: palette.border,
  text: palette.cream,
  textMuted: palette.muted,
  onPrimary: palette.black,
  primary: palette.cream,
  accent: palette.orange,
  info: palette.amber,
  danger: palette.coral,
  highlight: palette.navy,
  onHighlight: palette.cream,
  online: palette.online,
  overlay: 'rgba(0,0,0,0.72)',
  pastel,
  onPastel: '#1A1714',
};

export const lightColors: ThemeColors = {
  background: palette.creamBg,
  surface: palette.creamSurface,
  surfaceRaised: palette.creamRaised,
  border: palette.creamBorder,
  text: '#1A1714',
  textMuted: palette.mutedLight,
  onPrimary: palette.cream,
  primary: '#1A1714',
  accent: palette.orange,
  info: '#C98F25',
  danger: '#D9503F',
  highlight: palette.navy,
  onHighlight: palette.cream,
  online: '#2FB566',
  overlay: 'rgba(26,23,20,0.45)',
  pastel,
  onPastel: '#1A1714',
};

export const spacing = {
  xxs: 4,
  xs: 8,
  sm: 12,
  md: 16,
  lg: 20,
  xl: 24,
  xxl: 32,
  xxxl: 48,
} as const;

export const radius = {
  sm: 12,
  md: 16,
  card: 20,
  pill: 999,
} as const;

/** Minimum touch target (Android guideline: 48dp). */
export const touchTarget = 48;

export const fonts = {
  serif: 'InstrumentSerif_400Regular',
  serifItalic: 'InstrumentSerif_400Regular_Italic',
  sans: 'DMSans_400Regular',
  sansMedium: 'DMSans_500Medium',
  sansSemiBold: 'DMSans_600SemiBold',
  sansBold: 'DMSans_700Bold',
} as const;

export const typography = {
  display: { fontFamily: fonts.serif, fontSize: 40, lineHeight: 44, letterSpacing: -0.4 },
  title: { fontFamily: fonts.serif, fontSize: 34, lineHeight: 38, letterSpacing: -0.3 },
  headline: { fontFamily: fonts.serif, fontSize: 26, lineHeight: 30, letterSpacing: -0.2 },
  subhead: { fontFamily: fonts.serif, fontSize: 21, lineHeight: 25 },
  bodyLarge: { fontFamily: fonts.sans, fontSize: 16, lineHeight: 22 },
  body: { fontFamily: fonts.sans, fontSize: 14, lineHeight: 20 },
  bodyBold: { fontFamily: fonts.sansBold, fontSize: 14, lineHeight: 20 },
  label: { fontFamily: fonts.sansSemiBold, fontSize: 14, lineHeight: 19 },
  caption: { fontFamily: fonts.sans, fontSize: 12, lineHeight: 17 },
  captionBold: { fontFamily: fonts.sansSemiBold, fontSize: 12, lineHeight: 17 },
  micro: { fontFamily: fonts.sansMedium, fontSize: 10.5, lineHeight: 13, letterSpacing: 0.3 },
} as const;

export type TypographyVariant = keyof typeof typography;

export const motion = {
  fast: 200,
  normal: 260,
  slow: 300,
  spring: { damping: 18, stiffness: 260, mass: 0.8 },
  springBouncy: { damping: 11, stiffness: 180, mass: 0.7 },
  pressScale: 0.96,
} as const;

/** Background-glow tints for onboarding slides (one per slide). */
export const onboardingTints = [palette.lavender, palette.peach, palette.mint, palette.sky] as const;
