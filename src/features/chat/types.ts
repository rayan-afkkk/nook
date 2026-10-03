import type { Timestamp } from '@react-native-firebase/firestore';

export type ChatType = 'direct' | 'group';
export type Disappearing = 'off' | '24h' | '7d';
export type MessageKind = 'text' | 'image' | 'file' | 'voice' | 'gif' | 'sticker' | 'system';

/** Media lives on Cloudinary (uploads) or Giphy (GIFs/stickers); Firestore only stores the reference. */
export type Media = {
  url: string;
  publicId?: string;
  resourceType?: 'image' | 'video' | 'raw';
  width?: number;
  height?: number;
  size?: number;
  name?: string;
  mime?: string;
  durationMs?: number;
  /** 0..1 amplitudes, ~40 samples, for voice notes. */
  waveform?: number[];
  /** Small still preview for GIFs. */
  previewUrl?: string;
};

export type ReplyRef = { id: string; senderId: string; kind: MessageKind; text: string };

/** chats/{chatId}/messages/{messageId} */
export type Message = {
  id: string;
  senderId: string;
  kind: MessageKind;
  text?: string;
  media?: Media;
  replyTo?: ReplyRef | null;
  reactions?: Record<string, string>;
  forwarded?: boolean;
  createdAt: Timestamp | null;
  expireAt?: Timestamp | null;
  /** Local only: write not yet acknowledged by the server. */
  pending?: boolean;
};

export type LastMessage = { senderId: string; kind: MessageKind | 'deleted'; preview: string };

/** chats/{chatId} */
export type Chat = {
  id: string;
  type: ChatType;
  members: string[];
  name?: string | null;
  photoURL?: string | null;
  createdBy: string;
  createdAt: Timestamp | null;
  lastMessage?: LastMessage | null;
  lastMessageAt: Timestamp | null;
  lastRead?: Record<string, Timestamp>;
  mutedBy?: string[];
  disappearing?: Disappearing;
};
