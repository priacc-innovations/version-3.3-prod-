// src/store/notificationStore.ts
import { create } from "zustand";
import api from "../api/axiosInstance";

export interface Notification {
  id: string;
  title?: string;
  message: string;
  timestamp: string;
  read: boolean;
}

// how backend returns announcements
interface BackendAnnouncement {
  id: number;
  title: string;
  message: string;
  targetRole: string;   // "all", "employee", "hr", "trainer"
  createdAt: string;    // ISO
}

interface State {
  notifications: Notification[];
  unreadCount: number;
  dismissedIds: string[];

  add: (notif: Notification) => void;
  markRead: (id: string) => void;
  markAll: () => void;
  dismiss: (id: string) => void;            // 👈 close one notif
  clearAll: () => void;
  loadForRole: (role: string) => Promise<void>; // fetch from backend
}

const DISMISSED_KEY = "dismissedAnnouncements";

const loadInitialDismissed = (): string[] => {
  if (typeof window === "undefined") return [];
  try {
    const raw = localStorage.getItem(DISMISSED_KEY);
    return raw ? JSON.parse(raw) : [];
  } catch {
    return [];
  }
};

const saveDismissed = (ids: string[]) => {
  if (typeof window === "undefined") return;
  try {
    localStorage.setItem(DISMISSED_KEY, JSON.stringify(ids));
  } catch {
    // ignore
  }
};

export const useNotificationStore = create<State>((set, get) => ({
  notifications: [],
  unreadCount: 0,
  dismissedIds: loadInitialDismissed(),

  add: (notif) =>
    set((state) => ({
      notifications: [notif, ...state.notifications],
      unreadCount: state.unreadCount + (notif.read ? 0 : 1),
    })),

  markRead: (id) =>
    set((state) => {
      const updated = state.notifications.map((n) =>
        n.id === id ? { ...n, read: true } : n
      );
      const wasUnread = state.notifications.find((n) => n.id === id && !n.read);
      return {
        notifications: updated,
        unreadCount: Math.max(0, state.unreadCount - (wasUnread ? 1 : 0)),
      };
    }),

  markAll: () =>
    set((state) => ({
      notifications: state.notifications.map((n) => ({ ...n, read: true })),
      unreadCount: 0,
    })),

  // 🔥 user clicked close on ONE notification
  dismiss: (id) =>
    set((state) => {
      const newDismissed = Array.from(new Set([...state.dismissedIds, id]));
      saveDismissed(newDismissed);

      const remaining = state.notifications.filter((n) => n.id !== id);
      return {
        dismissedIds: newDismissed,
        notifications: remaining,
        unreadCount: Math.max(0, remaining.length),
      };
    }),

  clearAll: () =>
    set(() => ({
      notifications: [],
      unreadCount: 0,
    })),

  // 🔥 fetch announcements for this role, skip dismissed ones
  loadForRole: async (role: string) => {
    try {
      const resp = await api.get<BackendAnnouncement[]>("/announcements/recent");
      const { dismissedIds } = get();

      const filtered = resp.data
        .filter(
          (a) =>
            a.targetRole === "all" ||
            a.targetRole === role
        )
        .filter((a) => !dismissedIds.includes(String(a.id)));

      const mapped: Notification[] = filtered.map((a) => ({
        id: String(a.id),
        title: a.title,
        message: a.message,
        timestamp: a.createdAt,
        read: false,
      }));

      set({
        notifications: mapped,
        unreadCount: mapped.length,
      });
    } catch (err) {
      console.error("Failed to load announcements for role:", role, err);
    }
  },
}));