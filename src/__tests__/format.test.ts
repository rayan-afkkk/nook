import { formatBytes, initials } from '@/lib/format';

describe('format', () => {
  it('formats bytes', () => {
    expect(formatBytes(0)).toBe('0 MB');
    expect(formatBytes(512)).toBe('512 B');
    expect(formatBytes(1536)).toBe('1.5 KB');
    expect(formatBytes(250 * 1024 * 1024)).toBe('250 MB');
  });

  it('builds initials', () => {
    expect(initials('Ali Khan')).toBe('AK');
    expect(initials('sam')).toBe('S');
    expect(initials('  ')).toBe('?');
    expect(initials('Mary Jane Watson')).toBe('MW');
  });
});
