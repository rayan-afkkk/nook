import {
  RecordingPresets,
  getRecordingPermissionsAsync,
  requestRecordingPermissionsAsync,
  setAudioModeAsync,
  useAudioRecorder,
  useAudioRecorderState,
} from 'expo-audio';
import { File } from 'expo-file-system';
import { useCallback, useEffect, useRef } from 'react';

import { toWaveform } from '@/features/chat/model';
import { UserFacingError } from '@/lib/errors';

export type VoiceClip = { uri: string; durationMs: number; waveform: number[]; size?: number };

const MIN_MS = 700;

/** Hold-to-record voice notes (AAC in .m4a) with metering for the waveform. */
export function useVoiceRecorder() {
  const recorder = useAudioRecorder({ ...RecordingPresets.HIGH_QUALITY, isMeteringEnabled: true });
  const state = useAudioRecorderState(recorder, 80);
  const samples = useRef<number[]>([]);
  const starting = useRef<Promise<boolean> | null>(null);
  const startedAt = useRef(0);

  useEffect(() => {
    if (state.isRecording && typeof state.metering === 'number') samples.current.push(state.metering);
  }, [state.isRecording, state.metering, state.durationMillis]);

  const start = useCallback(async (): Promise<boolean> => {
    const run = async () => {
      let perm = await getRecordingPermissionsAsync();
      if (!perm.granted) perm = await requestRecordingPermissionsAsync();
      if (!perm.granted) throw new UserFacingError('Microphone access is off. Turn it on in Android settings.');
      await setAudioModeAsync({ allowsRecording: true, playsInSilentMode: true });
      await recorder.prepareToRecordAsync();
      samples.current = [];
      startedAt.current = Date.now();
      recorder.record();
      return true;
    };
    starting.current = run();
    return starting.current;
  }, [recorder]);

  /** Stops; returns the clip, or null when cancelled or too short. */
  const stop = useCallback(
    async (cancel: boolean): Promise<VoiceClip | null> => {
      try {
        await starting.current;
      } catch {
        return null;
      }
      starting.current = null;
      try {
        await recorder.stop();
      } catch {
        return null;
      } finally {
        void setAudioModeAsync({ allowsRecording: false }).catch(() => undefined);
      }
      const durationMs = Date.now() - startedAt.current;
      const uri = recorder.uri;
      if (!uri) return null;
      if (cancel || durationMs < MIN_MS) {
        try {
          new File(uri).delete();
        } catch {
          // temp file; ignore
        }
        return null;
      }
      let size: number | undefined;
      try {
        size = new File(uri).size ?? undefined;
      } catch {
        size = undefined;
      }
      return { uri, durationMs, waveform: toWaveform(samples.current), size };
    },
    [recorder],
  );

  return { start, stop, recording: state.isRecording, durationMs: state.durationMillis };
}
