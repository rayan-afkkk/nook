import { AudioSession } from '@livekit/react-native';
import { LocalVideoTrack, Room, RoomEvent, Track, type VideoTrack } from 'livekit-client';
import { useCallback, useEffect, useRef, useState } from 'react';

import { describeError } from '@/lib/errors';

import { fetchCallToken } from './callService';

export type RoomState = {
  connection: 'idle' | 'connecting' | 'connected' | 'reconnecting' | 'error';
  error: string | null;
  remoteJoined: boolean;
  remoteLeft: boolean;
  remoteVideo?: VideoTrack;
  localVideo?: VideoTrack;
  muted: boolean;
  cameraOn: boolean;
  speaker: boolean;
  facing: 'user' | 'environment';
};

/** One LiveKit room per call (room name = call id). The Worker issues the access token. */
export function useCallRoom(callId: string, video: boolean, enabled: boolean) {
  const room = useRef<Room | null>(null);
  const joinedOnce = useRef(false);
  const [state, setState] = useState<RoomState>({
    connection: 'idle',
    error: null,
    remoteJoined: false,
    remoteLeft: false,
    muted: false,
    cameraOn: video,
    speaker: video,
    facing: 'user',
  });

  const refresh = useCallback(() => {
    const r = room.current;
    if (!r) return;
    const remote = [...r.remoteParticipants.values()][0];
    const remotePub = remote?.getTrackPublication(Track.Source.Camera);
    const localPub = r.localParticipant.getTrackPublication(Track.Source.Camera);
    if (remote) joinedOnce.current = true;
    setState((s) => ({
      ...s,
      remoteJoined: !!remote,
      remoteLeft: joinedOnce.current && !remote,
      remoteVideo: remotePub && !remotePub.isMuted ? (remotePub.videoTrack as VideoTrack | undefined) : undefined,
      localVideo: localPub && !localPub.isMuted ? (localPub.videoTrack as VideoTrack | undefined) : undefined,
    }));
  }, []);

  useEffect(() => {
    if (!enabled) return;
    let cancelled = false;
    const r = new Room({ adaptiveStream: true, dynacast: true });
    room.current = r;
    const events = [
      RoomEvent.ParticipantConnected,
      RoomEvent.ParticipantDisconnected,
      RoomEvent.TrackSubscribed,
      RoomEvent.TrackUnsubscribed,
      RoomEvent.TrackMuted,
      RoomEvent.TrackUnmuted,
      RoomEvent.LocalTrackPublished,
      RoomEvent.LocalTrackUnpublished,
    ];
    events.forEach((e) => r.on(e, refresh));
    r.on(RoomEvent.Reconnecting, () => setState((s) => ({ ...s, connection: 'reconnecting' })));
    r.on(RoomEvent.Reconnected, () => setState((s) => ({ ...s, connection: 'connected' })));

    (async () => {
      setState((s) => ({ ...s, connection: 'connecting' }));
      try {
        const { token, url } = await fetchCallToken(callId);
        if (cancelled) return;
        await AudioSession.startAudioSession();
        await r.connect(url, token);
        if (cancelled) return;
        await r.localParticipant.setMicrophoneEnabled(true);
        if (video) await r.localParticipant.setCameraEnabled(true, { facingMode: 'user' });
        await AudioSession.selectAudioOutput(video ? 'speaker' : 'earpiece').catch(() => undefined);
        setState((s) => ({ ...s, connection: 'connected' }));
        refresh();
      } catch (e) {
        if (!cancelled) setState((s) => ({ ...s, connection: 'error', error: describeError(e, 'Couldn’t connect the call.') }));
      }
    })();

    return () => {
      cancelled = true;
      events.forEach((e) => r.off(e, refresh));
      void r.disconnect();
      void AudioSession.stopAudioSession();
      room.current = null;
    };
  }, [callId, enabled, video, refresh]);

  const toggleMute = useCallback(async () => {
    const r = room.current;
    if (!r) return;
    const next = !state.muted;
    await r.localParticipant.setMicrophoneEnabled(!next);
    setState((s) => ({ ...s, muted: next }));
  }, [state.muted]);

  const toggleCamera = useCallback(async () => {
    const r = room.current;
    if (!r) return;
    const next = !state.cameraOn;
    await r.localParticipant.setCameraEnabled(next, { facingMode: state.facing });
    setState((s) => ({ ...s, cameraOn: next }));
    refresh();
  }, [state.cameraOn, state.facing, refresh]);

  const toggleSpeaker = useCallback(async () => {
    const next = !state.speaker;
    await AudioSession.selectAudioOutput(next ? 'speaker' : 'earpiece').catch(() => undefined);
    setState((s) => ({ ...s, speaker: next }));
  }, [state.speaker]);

  const flip = useCallback(async () => {
    const track = room.current?.localParticipant.getTrackPublication(Track.Source.Camera)?.track;
    if (!(track instanceof LocalVideoTrack)) return;
    const facing = state.facing === 'user' ? 'environment' : 'user';
    await track.restartTrack({ facingMode: facing });
    setState((s) => ({ ...s, facing }));
  }, [state.facing]);

  const hangUp = useCallback(async () => {
    await room.current?.disconnect();
  }, []);

  return { state, toggleMute, toggleCamera, toggleSpeaker, flip, hangUp };
}
