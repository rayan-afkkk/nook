/** The chat currently on screen, so foreground notifications for it are suppressed. */
let active: string | null = null;
export const setActiveChat = (id: string | null) => {
  active = id;
};
export const getActiveChat = () => active;
