import { FlashList, type FlashListRef } from '@shopify/flash-list';
import { router, useFocusEffect, useLocalSearchParams } from 'expo-router';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ActivityIndicator, StyleSheet, View, type NativeScrollEvent, type NativeSyntheticEvent } from 'react-native';
import { useReanimatedKeyboardAnimation } from 'react-native-keyboard-controller';
import Animated, { useAnimatedStyle } from 'react-native-reanimated';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';

import { Button, EmptyState, ListRow, Sheet, Skeleton, Text, toast } from '@/components/ui';
import { startCall } from '@/features/calls/callService';
import { setActiveChat } from '@/features/chat/activeChat';
import {
  cleanupExpired,
  deleteMessage,
  markRead,
  sendMessage,
  setDisappearing,
  setReaction,
} from '@/features/chat/chatService';
import { ChatPickerSheet } from '@/features/chat/components/ChatPickerSheet';
import { ChatHeader } from '@/features/chat/components/ChatHeader';
import { Composer } from '@/features/chat/components/Composer';
import { DateSeparator } from '@/features/chat/components/DateSeparator';
import { ImageViewer } from '@/features/chat/components/ImageViewer';
import { MessageActionsSheet } from '@/features/chat/components/MessageActionsSheet';
import { MessageBubble } from '@/features/chat/components/MessageBubble';
import { OutboxBubble } from '@/features/chat/components/OutboxBubble';
import { TypingDots } from '@/features/chat/components/TypingDots';
import { dayLabel, otherMember, previewFor, sameDay } from '@/features/chat/model';
import type { Chat, Disappearing, Message, ReplyRef } from '@/features/chat/types';
import { useChatDoc } from '@/features/chat/useChatDoc';
import { useMessages } from '@/features/chat/useMessages';
import { setBlocked, useIsBlocked } from '@/features/friends/privateDoc';
import { useSession } from '@/features/auth/session';
import { enqueueMedia, useOutbox, type OutboxItem } from '@/features/media/outbox';
import { pickFile, pickPhoto } from '@/features/media/pickers';
import { setTyping, usePresence, useTyping } from '@/features/presence/presence';
import { displayNameOf, useProfile, useProfiles } from '@/features/profile/profiles';
import { describeError } from '@/lib/errors';
import { haptics } from '@/lib/haptics';
import { spacing, useTheme } from '@/theme';

type Item =
  | { type: 'date'; key: string; label: string }
  | { type: 'message'; key: string; message: Message; chained: boolean; showSender: boolean; footer: string | null }
  | { type: 'outbox'; key: string; item: OutboxItem }
  | { type: 'typing'; key: string };

const TIMER_OPTIONS: { value: Disappearing; title: string; subtitle: string }[] = [
  { value: 'off', title: 'Off', subtitle: 'Messages stay until someone deletes them' },
  { value: '24h', title: '24 hours', subtitle: 'New messages vanish a day after they’re sent' },
  { value: '7d', title: '7 days', subtitle: 'New messages vanish a week after they’re sent' },
];

function relativeLastSeen(ms: number | null, now: number): string {
  if (!ms) return 'Offline';
  const mins = Math.round((now - ms) / 60000);
  if (mins < 1) return 'Last seen just now';
  if (mins < 60) return `Last seen ${mins} min ago`;
  const hours = Math.round(mins / 60);
  if (hours < 24) return `Last seen ${hours} h ago`;
  return `Last seen ${new Date(ms).toLocaleDateString(undefined, { day: 'numeric', month: 'short' })}`;
}

export default function ChatScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const chatId = String(id);
  const me = useSession((s) => s.user?.uid ?? '');
  const { chat, missing } = useChatDoc(chatId);
  if (missing) {
    return (
      <SafeAreaView style={styles.flex}>
        <EmptyState
          icon="chatbubble-ellipses-outline"
          title="Chat not available"
          message="It may have been removed, or you’re no longer a member."
          action={<Button title="Back to chats" onPress={() => router.replace('/chats')} />}
        />
      </SafeAreaView>
    );
  }
  if (!chat || !me) return <ChatSkeleton />;
  return <ChatView chat={chat} me={me} />;
}

function ChatSkeleton() {
  const { colors } = useTheme();
  return (
    <SafeAreaView style={[styles.flex, { backgroundColor: colors.background }]}>
      <View style={styles.skeletonHeader}>
        <Skeleton width={40} height={40} radius={20} />
        <Skeleton width={140} height={16} />
      </View>
      <View style={styles.skeletonBody}>
        {[0.6, 0.4, 0.7, 0.5].map((w, i) => (
          <Skeleton key={i} width={`${w * 100}%`} height={44} radius={22} style={{ alignSelf: i % 2 ? 'flex-end' : 'flex-start' }} />
        ))}
      </View>
    </SafeAreaView>
  );
}

function ChatView({ chat, me }: { chat: Chat; me: string }) {
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  const keyboard = useReanimatedKeyboardAnimation();
  // Android edge-to-edge: the composer sits above the navigation bar (gesture pill or 3 buttons) when the
  // keyboard is closed, and directly on top of the keyboard when it's open. Keyboard height is measured
  // from the bottom of the screen, so it already includes the nav bar: take the larger, never both.
  const bottomStyle = useAnimatedStyle(() => ({ paddingBottom: Math.max(-keyboard.height.value, insets.bottom) }));
  const list = useRef<FlashListRef<Item>>(null);
  const { messages, status: loadStatus, error, hasMore, loadingMore, loadMore } = useMessages(chat.id);
  const outboxAll = useOutbox((s) => s.items);
  const outbox = useMemo(() => outboxAll.filter((i) => i.chatId === chat.id), [outboxAll, chat.id]);
  const profiles = useProfiles((s) => s.byId);
  const otherId = chat.type === 'direct' ? otherMember(chat.members, me) : undefined;
  const other = useProfile(otherId);
  const presence = usePresence(otherId);
  const typing = useTyping(chat.id, me);
  const blocked = useIsBlocked(otherId);
  const [now, setNow] = useState(() => Date.now());
  const [replyTo, setReplyTo] = useState<ReplyRef | null>(null);
  const [selected, setSelected] = useState<Message | null>(null);
  const [viewer, setViewer] = useState<string | null>(null);
  const [timerOpen, setTimerOpen] = useState(false);
  const [forwarding, setForwarding] = useState<Message | null>(null);
  const atBottom = useRef(true);
  const lastMarked = useRef(0);

  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 60_000);
    return () => clearInterval(t);
  }, []);

  const nameOf = useCallback((uid: string) => (uid === me ? 'You' : displayNameOf(profiles[uid])), [me, profiles]);
  const title = chat.type === 'group' ? (chat.name ?? 'Group') : displayNameOf(other);

  // Focus: mark this chat active (no foreground notifications for it) and clean up expired messages once.
  useFocusEffect(
    useCallback(() => {
      setActiveChat(chat.id);
      void cleanupExpired(chat, me).catch(() => undefined);
      return () => {
        setActiveChat(null);
        setTyping(chat.id, me, false);
      };
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [chat.id, me]),
  );

  // Read receipts: one write when there's something new and we're looking at the bottom.
  const lastAt = chat.lastMessageAt?.toMillis() ?? 0;
  const myRead = chat.lastRead?.[me]?.toMillis() ?? 0;
  const maybeMarkRead = useCallback(() => {
    if (!atBottom.current || lastAt <= myRead || chat.lastMessage?.senderId === me) return;
    if (Date.now() - lastMarked.current < 4000) return;
    lastMarked.current = Date.now();
    void markRead(chat.id, me).catch(() => undefined);
  }, [chat.id, chat.lastMessage?.senderId, lastAt, me, myRead]);
  useFocusEffect(maybeMarkRead);
  useEffect(maybeMarkRead, [maybeMarkRead, messages.length]);

  const items = useMemo<Item[]>(() => {
    const asc = [...messages].reverse().filter((m) => !(m.senderId !== me && blocked));
    const myLast = [...asc].reverse().find((m) => m.senderId === me);
    const lastRead: Record<string, number> = {};
    Object.entries(chat.lastRead ?? {}).forEach(([k, v]) => (lastRead[k] = v?.toMillis?.() ?? 0));
    const others = chat.members.filter((m) => m !== me);
    const out: Item[] = [];
    let prev: Message | null = null;
    asc.forEach((m, i) => {
      const t = m.createdAt?.toMillis() ?? now;
      if (!prev || !sameDay(prev.createdAt?.toMillis() ?? t, t)) out.push({ type: 'date', key: `d-${t}`, label: dayLabel(t, now) });
      const next = asc[i + 1];
      const chained = !!next && next.senderId === m.senderId && (next.createdAt?.toMillis() ?? t) - t < 3 * 60_000;
      const showSender = chat.type === 'group' && m.senderId !== me && (!prev || prev.senderId !== m.senderId);
      let footer: string | null = null;
      if (myLast && m.id === myLast.id && !m.pending) {
        const seenBy = others.filter((o) => (lastRead[o] ?? 0) >= t).length;
        footer = seenBy === 0 ? 'Sent' : chat.type === 'direct' || seenBy === others.length ? 'Seen' : `Seen by ${seenBy}`;
      }
      out.push({ type: 'message', key: m.id, message: m, chained, showSender, footer });
      prev = m;
    });
    outbox.forEach((o) => out.push({ type: 'outbox', key: o.localId, item: o }));
    if (typing.length) out.push({ type: 'typing', key: 'typing' });
    return out;
  }, [messages, outbox, typing.length, chat.lastRead, chat.members, chat.type, me, now, blocked]);

  const latestId = messages[0]?.id;

  const headerStatus = (() => {
    if (typing.length) {
      return chat.type === 'group' ? `${typing.map((u) => nameOf(u).split(' ')[0]).join(', ')} typing…` : 'typing…';
    }
    if (chat.type === 'group') return `${chat.members.length} members`;
    if (blocked) return 'Blocked';
    return presence.online ? 'Online' : relativeLastSeen(presence.lastChanged, now);
  })();

  const send = (draft: Parameters<typeof sendMessage>[2]) => {
    sendMessage(chat, me, { ...draft, replyTo }).catch((e) => toast.error(describeError(e, "Couldn't send. It will retry when you're back online.")));
    setReplyTo(null);
    requestAnimationFrame(() => list.current?.scrollToEnd({ animated: true }));
  };

  const reply = (m: Message) => {
    haptics.light();
    setReplyTo({ id: m.id, senderId: m.senderId, kind: m.kind, text: previewFor(m.kind, m.text, m.media?.name).slice(0, 100) });
  };

  const attach = async (source: 'library' | 'camera' | 'file') => {
    try {
      if (source === 'file') {
        const f = await pickFile();
        if (f) enqueueMedia(chat, me, { kind: 'file', uri: f.uri, name: f.name, mime: f.mime, size: f.size, replyTo });
      } else {
        const p = await pickPhoto(source);
        if (p) enqueueMedia(chat, me, { kind: 'image', uri: p.uri, name: p.name, mime: p.mime, width: p.width, height: p.height, replyTo });
      }
      setReplyTo(null);
      requestAnimationFrame(() => list.current?.scrollToEnd({ animated: true }));
    } catch (e) {
      toast.error(describeError(e));
    }
  };

  const onScroll = (e: NativeSyntheticEvent<NativeScrollEvent>) => {
    const { contentOffset, layoutMeasurement, contentSize } = e.nativeEvent;
    const bottom = contentOffset.y + layoutMeasurement.height >= contentSize.height - 80;
    if (bottom && !atBottom.current) {
      atBottom.current = true;
      maybeMarkRead();
    } else if (!bottom) {
      atBottom.current = false;
    }
  };

  const call = (video: boolean) => {
    if (!otherId) return;
    if (blocked) {
      toast.show('Unblock them to call.');
      return;
    }
    startCall(chat.id, me, otherId, video)
      .then((callId) => router.push({ pathname: '/call/[id]', params: { id: callId } }))
      .catch((e) => toast.error(describeError(e)));
  };

  return (
    <SafeAreaView edges={['top']} style={[styles.flex, { backgroundColor: colors.background }]}>
      <ChatHeader
        chat={chat}
        title={title}
        photoURL={other?.photoURL}
        online={presence.online}
        status={headerStatus}
        seed={otherId ?? chat.id}
        onCall={chat.type === 'direct' ? call : undefined}
        onOpenInfo={() => router.push({ pathname: '/chat-info/[id]', params: { id: chat.id } })}
        onTimer={() => setTimerOpen(true)}
      />
      <Animated.View style={[styles.flex, styles.clip, bottomStyle]}>
        {loadStatus === 'loading' && items.length === 0 ? (
          <View style={styles.center}>
            <ActivityIndicator color={colors.textMuted} />
          </View>
        ) : error && items.length === 0 ? (
          <EmptyState icon="cloud-offline-outline" title="Couldn’t load messages" message={error} />
        ) : items.length === 0 ? (
          <EmptyState icon="chatbubbles-outline" title="Say hi" message={`This is the start of your chat${chat.type === 'group' ? ` in ${title}` : ` with ${title}`}.`} />
        ) : (
          <FlashList
            ref={list}
            data={items}
            keyExtractor={(i) => i.key}
            getItemType={(i) => (i.type === 'message' ? `m-${i.message.kind}` : i.type)}
            maintainVisibleContentPosition={{ startRenderingFromBottom: true, autoscrollToBottomThreshold: 0.25 }}
            onStartReached={hasMore ? () => void loadMore() : undefined}
            onStartReachedThreshold={0.4}
            onScroll={onScroll}
            // Keyboard or emoji panel opened: stay pinned to the newest message.
            onLayout={() => {
              if (atBottom.current) requestAnimationFrame(() => list.current?.scrollToEnd({ animated: false }));
            }}
            scrollEventThrottle={64}
            keyboardDismissMode="interactive"
            keyboardShouldPersistTaps="handled"
            contentContainerStyle={styles.listContent}
            ListHeaderComponent={loadingMore ? <ActivityIndicator style={styles.loadingMore} color={colors.textMuted} /> : null}
            renderItem={({ item }) => {
              switch (item.type) {
                case 'date':
                  return <DateSeparator label={item.label} />;
                case 'typing':
                  return <TypingDots />;
                case 'outbox':
                  return <OutboxBubble item={item.item} me={me} />;
                default: {
                  const m = item.message;
                  return (
                    <MessageBubble
                      message={m}
                      me={me}
                      mine={m.senderId === me}
                      senderName={item.showSender ? nameOf(m.senderId) : null}
                      replyAuthor={m.replyTo ? nameOf(m.replyTo.senderId) : undefined}
                      chained={item.chained}
                      footer={item.footer}
                      now={now}
                      onReply={reply}
                      onLongPress={setSelected}
                      onOpenImage={(msg) => setViewer(msg.media?.url ?? null)}
                      onToggleReaction={(msg, emoji) => {
                        const mineEmoji = msg.reactions?.[me];
                        void setReaction(chat.id, msg.id, me, mineEmoji === emoji ? null : emoji).catch((e) => toast.error(describeError(e)));
                      }}
                    />
                  );
                }
              }
            }}
          />
        )}
        {blocked ? (
          <View style={[styles.blocked, { borderTopColor: colors.border }]}>
            <Text variant="caption" color="textMuted" style={styles.flex}>
              You blocked {title}. They can’t message or call you.
            </Text>
            <Button title="Unblock" block={false} variant="secondary" onPress={() => otherId && void setBlocked(me, otherId, false)} />
          </View>
        ) : (
          <Composer
            replyTo={replyTo}
            replyAuthor={replyTo ? nameOf(replyTo.senderId) : ''}
            onCancelReply={() => setReplyTo(null)}
            onSendText={(text) => send({ kind: 'text', text })}
            onTyping={(t) => setTyping(chat.id, me, t)}
            onAttach={(s) => void attach(s)}
            onVoice={(clip) => {
              enqueueMedia(chat, me, {
                kind: 'voice',
                uri: clip.uri,
                name: `voice-${Date.now()}.m4a`,
                mime: 'audio/mp4',
                size: clip.size,
                durationMs: clip.durationMs,
                waveform: clip.waveform,
                replyTo,
              });
              setReplyTo(null);
            }}
            onGiphy={(item, type) =>
              send({
                kind: type === 'gifs' ? 'gif' : 'sticker',
                media: { url: item.url, previewUrl: item.previewUrl, width: item.width, height: item.height },
              })
            }
            onSticker={(s) => send({ kind: 'sticker', media: { url: s.url, width: 512, height: 512 } })}
          />
        )}
      </Animated.View>

      <MessageActionsSheet
        message={selected}
        mine={selected?.senderId === me}
        myReaction={selected?.reactions?.[me]}
        onClose={() => setSelected(null)}
        onReact={(emoji) => {
          if (selected) void setReaction(chat.id, selected.id, me, emoji).catch((e) => toast.error(describeError(e)));
          setSelected(null);
        }}
        onReply={() => {
          if (selected) reply(selected);
          setSelected(null);
        }}
        onForward={() => {
          setForwarding(selected);
          setSelected(null);
        }}
        onDelete={() => {
          const m = selected;
          setSelected(null);
          if (m) void deleteMessage(chat, me, m, latestId).then(() => haptics.success()).catch((e) => toast.error(describeError(e)));
        }}
      />

      <Sheet visible={timerOpen} onClose={() => setTimerOpen(false)} accessibilityLabel="Disappearing messages">
        <Text variant="headline">Disappearing messages</Text>
        <Text variant="caption" color="textMuted" style={styles.sheetText}>
          Applies to new messages for everyone in this chat. Someone could still screenshot a message before it disappears.
        </Text>
        {TIMER_OPTIONS.map((o) => (
          <ListRow
            key={o.value}
            icon={(chat.disappearing ?? 'off') === o.value ? 'radio-button-on' : 'radio-button-off'}
            title={o.title}
            subtitle={o.subtitle}
            showChevron={false}
            onPress={() => {
              setTimerOpen(false);
              void setDisappearing(chat.id, o.value)
                .then(() => haptics.success())
                .catch((e) => toast.error(describeError(e)));
            }}
          />
        ))}
      </Sheet>

      <ForwardSheet message={forwarding} me={me} onClose={() => setForwarding(null)} />
      <ImageViewer uri={viewer} onClose={() => setViewer(null)} />
    </SafeAreaView>
  );
}

function ForwardSheet({ message, me, onClose }: { message: Message | null; me: string; onClose: () => void }) {
  return (
    <ChatPickerSheet
      visible={!!message}
      me={me}
      title="Forward to"
      onClose={onClose}
      onPick={(c, name) => {
        if (!message) return;
        onClose();
        sendMessage(c, me, { kind: message.kind, text: message.text, media: message.media, forwarded: true })
          .then(() => toast.success(`Forwarded to ${name}`))
          .catch((e) => toast.error(describeError(e)));
      }}
    />
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  // The message list never draws over the header.
  clip: { overflow: 'hidden' },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  listContent: { paddingBottom: spacing.sm },
  loadingMore: { paddingVertical: spacing.md },
  blocked: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm, padding: spacing.md, borderTopWidth: StyleSheet.hairlineWidth },
  sheetText: { marginBottom: spacing.sm },
  skeletonHeader: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm, padding: spacing.md },
  skeletonBody: { padding: spacing.md, gap: spacing.sm },
});
