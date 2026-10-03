import { normalizeUsername, USERNAME_PATTERN, validateUsername } from '@/features/profile/username';

describe('usernames', () => {
  it('normalises case, whitespace and a leading @', () => {
    expect(normalizeUsername('  @Ali_K ')).toBe('ali_k');
  });

  it.each(['ali', 'ali.k', 'a_1', 'nook_fan', 'abcdefghijklmnopqrst'])('accepts %s', (name) => {
    expect(validateUsername(name)).toBeNull();
    expect(new RegExp(USERNAME_PATTERN).test(name)).toBe(true);
  });

  it.each([
    ['ab', /At least/],
    ['abcdefghijklmnopqrstu', /At most/],
    ['1ali', /Start with a letter/],
    ['ali-k', /Only letters/],
    ['ali.', /end with/],
    ['ali_', /end with/],
    ['a..b', /two dots/],
    ['admin', /reserved/],
  ])('rejects %s', (name, message) => {
    expect(validateUsername(name)).toMatch(message);
  });
});
