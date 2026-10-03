import {
  cooldownMs,
  createRecord,
  FREE_ATTEMPTS,
  parseRecord,
  safeEqual,
  validateSecret,
  verifySecret,
} from '@/features/lock/lockCrypto';

const salt = new Uint8Array(16).map((_, i) => i * 7);

describe('lock secret validation', () => {
  it('requires at least 6 characters', () => {
    expect(validateSecret('pin', '12345')).toMatch(/at least 6/);
    expect(validateSecret('password', 'abcde')).toMatch(/at least 6/);
    expect(validateSecret('password', 'abcdef')).toBeNull();
  });

  it('only allows digits in a PIN and rejects trivial PINs', () => {
    expect(validateSecret('pin', '12a456')).toMatch(/only contain numbers/);
    expect(validateSecret('pin', '111111')).toMatch(/repeating/);
    expect(validateSecret('pin', '123456')).toMatch(/sequences/);
    expect(validateSecret('pin', '654321')).toMatch(/sequences/);
    expect(validateSecret('pin', '480913')).toBeNull();
  });
});

describe('lock hashing', () => {
  it('verifies the right secret and rejects a wrong one', async () => {
    const record = await createRecord('pin', '480913', salt, false, 1000);
    expect(record.hash).toHaveLength(64);
    expect(record.salt).toHaveLength(32);
    expect(record.pinLength).toBe(6);
    expect(JSON.stringify(record)).not.toContain('480913');
    await expect(verifySecret(record, '480913')).resolves.toBe(true);
    await expect(verifySecret(record, '480914')).resolves.toBe(false);
  });

  it('uses the salt (same secret, different salt, different hash)', async () => {
    const a = await createRecord('password', 'hunter22', salt, false, 1000);
    const b = await createRecord('password', 'hunter22', new Uint8Array(16).fill(9), false, 1000);
    expect(a.hash).not.toEqual(b.hash);
    expect(a.pinLength).toBeUndefined();
  });

  it('round-trips through storage and rejects corrupt records', async () => {
    const record = await createRecord('password', 'hunter22', salt, true, 1000);
    expect(parseRecord(JSON.stringify(record))).toEqual(record);
    expect(parseRecord(null)).toBeNull();
    expect(parseRecord('{nope')).toBeNull();
    expect(parseRecord(JSON.stringify({ v: 2 }))).toBeNull();
  });

  it('compares digests in constant time', () => {
    expect(safeEqual('abcd', 'abcd')).toBe(true);
    expect(safeEqual('abcd', 'abce')).toBe(false);
    expect(safeEqual('abcd', 'abc')).toBe(false);
  });
});

describe('wrong-attempt throttling', () => {
  it('gives free attempts, then doubles the wait up to 15 minutes', () => {
    expect(cooldownMs(FREE_ATTEMPTS - 1)).toBe(0);
    expect(cooldownMs(FREE_ATTEMPTS)).toBe(30_000);
    expect(cooldownMs(FREE_ATTEMPTS + 1)).toBe(60_000);
    expect(cooldownMs(FREE_ATTEMPTS + 20)).toBe(15 * 60_000);
  });
});
