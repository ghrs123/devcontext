'use client';

import * as React from 'react';

import type { ToastActionElement, ToastProps } from '@/components/ui/toast';

const TOAST_LIMIT = 5;
const TOAST_REMOVE_DELAY = 4000;

type ToasterToast = ToastProps & {
  id: string;
  title?: React.ReactNode;
  description?: React.ReactNode;
  action?: ToastActionElement;
};

type ToastState = {
  toasts: ToasterToast[];
};

const listeners: Array<(state: ToastState) => void> = [];
let memoryState: ToastState = { toasts: [] };

function dispatch(state: ToastState) {
  memoryState = state;
  listeners.forEach((listener) => listener(memoryState));
}

function removeToast(id: string) {
  dispatch({
    toasts: memoryState.toasts.filter((toast) => toast.id !== id)
  });
}

function scheduleRemove(id: string) {
  window.setTimeout(() => removeToast(id), TOAST_REMOVE_DELAY);
}

export function toast({ ...props }: Omit<ToasterToast, 'id'>) {
  const id = crypto.randomUUID();
  const newToast: ToasterToast = {
    ...props,
    id,
    open: true,
    onOpenChange: (open) => {
      if (!open) {
        removeToast(id);
      }
    }
  };

  const nextToasts = [newToast, ...memoryState.toasts].slice(0, TOAST_LIMIT);
  dispatch({ toasts: nextToasts });
  scheduleRemove(id);

  return {
    id,
    dismiss: () => removeToast(id)
  };
}

export function useToast() {
  const [state, setState] = React.useState<ToastState>(memoryState);

  React.useEffect(() => {
    listeners.push(setState);
    return () => {
      const index = listeners.indexOf(setState);
      if (index > -1) {
        listeners.splice(index, 1);
      }
    };
  }, []);

  return {
    ...state,
    toast
  };
}
