import type { Timestamp } from '@react-native-firebase/firestore';

export type CallStatus = 'ringing' | 'answered' | 'declined' | 'missed' | 'cancelled' | 'ended';

/** calls/{callId}: one 1:1 call, readable by its two members. */
export type Call = {
  id: string;
  chatId: string;
  members: string[];
  callerId: string;
  calleeId: string;
  video: boolean;
  status: CallStatus;
  startedAt: Timestamp | null;
  answeredAt?: Timestamp | null;
  endedAt?: Timestamp | null;
};
