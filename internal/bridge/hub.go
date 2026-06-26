package bridge

import (
	"context"
	"sync"
)

type Hub struct {
	mu          sync.RWMutex
	subscribers map[int]chan MessageEvent
	nextID      int
	recent      []MessageEvent
	recentLimit int
}

func NewHub(recentLimit int) *Hub {
	if recentLimit <= 0 {
		recentLimit = 200
	}
	return &Hub{
		subscribers: map[int]chan MessageEvent{},
		recentLimit: recentLimit,
	}
}

func (h *Hub) Publish(event MessageEvent) {
	h.mu.Lock()
	h.recent = append(h.recent, event)
	if len(h.recent) > h.recentLimit {
		h.recent = h.recent[len(h.recent)-h.recentLimit:]
	}
	for _, ch := range h.subscribers {
		select {
		case ch <- event:
		default:
		}
	}
	h.mu.Unlock()
}

func (h *Hub) Subscribe(ctx context.Context) <-chan MessageEvent {
	ch := make(chan MessageEvent, 64)

	h.mu.Lock()
	id := h.nextID
	h.nextID++
	h.subscribers[id] = ch
	h.mu.Unlock()

	go func() {
		<-ctx.Done()
		h.mu.Lock()
		delete(h.subscribers, id)
		close(ch)
		h.mu.Unlock()
	}()

	return ch
}

func (h *Hub) Recent(limit int) []MessageEvent {
	h.mu.RLock()
	defer h.mu.RUnlock()
	if limit <= 0 || limit > len(h.recent) {
		limit = len(h.recent)
	}
	out := make([]MessageEvent, limit)
	copy(out, h.recent[len(h.recent)-limit:])
	return out
}
