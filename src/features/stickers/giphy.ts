import { GIPHY_API_KEY } from '@/constants/app';
import { UserFacingError } from '@/lib/errors';

export type GiphyItem = { id: string; url: string; previewUrl: string; width: number; height: number; title: string };
export type GiphyType = 'gifs' | 'stickers';

export const giphyConfigured = () => GIPHY_API_KEY.length > 0;

type Rendition = { url?: string; webp?: string; width?: string; height?: string };
type Raw = { id: string; title?: string; images: Record<string, Rendition | undefined> };

/** Trending when the query is empty; 24 results per page; PG-13. */
export async function searchGiphy(type: GiphyType, q: string, offset = 0): Promise<{ items: GiphyItem[]; next: number | null }> {
  if (!giphyConfigured()) throw new UserFacingError('GIF search is not set up yet (Giphy key missing in this build).');
  const params = new URLSearchParams({ api_key: GIPHY_API_KEY, limit: '24', offset: String(offset), rating: 'pg-13', bundle: 'messaging_non_clips' });
  const endpoint = q.trim() ? 'search' : 'trending';
  if (q.trim()) params.set('q', q.trim().slice(0, 50));
  const res = await fetch(`https://api.giphy.com/v1/${type}/${endpoint}?${params.toString()}`);
  if (!res.ok) throw new UserFacingError("Couldn't reach Giphy. Try again.");
  const body = (await res.json()) as { data: Raw[]; pagination?: { total_count?: number; count?: number; offset?: number } };
  const items = body.data
    .map((r): GiphyItem | null => {
      const main = r.images.fixed_width ?? r.images.downsized;
      const still = r.images.fixed_width_still ?? r.images.fixed_width_small_still;
      if (!main?.url) return null;
      return {
        id: r.id,
        url: main.webp ?? main.url,
        previewUrl: still?.url ?? main.url,
        width: Number(main.width ?? 200),
        height: Number(main.height ?? 200),
        title: r.title ?? '',
      };
    })
    .filter((x): x is GiphyItem => !!x);
  const p = body.pagination;
  const nextOffset = (p?.offset ?? offset) + (p?.count ?? items.length);
  return { items, next: p?.total_count && nextOffset < p.total_count && items.length ? nextOffset : null };
}
