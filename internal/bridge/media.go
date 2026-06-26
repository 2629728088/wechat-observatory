package bridge

import (
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"fmt"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"
)

const maxMediaBytes = 8 * 1024 * 1024

func (s *Service) StoreMediaAttachment(event MessageEvent) (MessageEvent, error) {
	if strings.TrimSpace(s.mediaDir) == "" || strings.TrimSpace(event.MediaBase64) == "" {
		event.MediaBase64 = ""
		return event, nil
	}
	decoded, detectedMime, err := decodeMediaBase64(event.MediaBase64)
	if err != nil {
		event.MediaBase64 = ""
		return event, err
	}
	event.MediaBase64 = ""
	if len(decoded) == 0 {
		return event, fmt.Errorf("media payload is empty")
	}
	if len(decoded) > maxMediaBytes {
		return event, fmt.Errorf("media payload too large")
	}
	if strings.TrimSpace(event.MediaMime) == "" {
		event.MediaMime = detectedMime
	}
	if strings.TrimSpace(event.MediaKind) == "" {
		event.MediaKind = mediaKindForMessage(event.MessageType, event.MediaMime)
	}
	hash := sha256.Sum256(decoded)
	digest := hex.EncodeToString(hash[:])
	name := strings.TrimSpace(event.MediaName)
	if name == "" {
		name = digest + mediaExtension(event.MediaKind, event.MediaMime, "")
	}
	relPath := filepath.Join(
		safePathPart(firstNonEmpty(event.Device, "device")),
		time.Unix(event.Timestamp(), 0).Format("20060102"),
		digest+mediaExtension(event.MediaKind, event.MediaMime, name),
	)
	fullPath, err := s.safeMediaPath(relPath)
	if err != nil {
		return event, err
	}
	if err := os.MkdirAll(filepath.Dir(fullPath), 0o755); err != nil {
		return event, err
	}
	if err := os.WriteFile(fullPath, decoded, 0o644); err != nil {
		return event, err
	}
	event.MediaName = name
	event.MediaSize = int64(len(decoded))
	event.MediaURL = "/api/media/" + filepath.ToSlash(relPath)
	return event, nil
}

func (s *Service) MediaFilePath(rawPath string) (string, error) {
	rel := strings.TrimPrefix(strings.TrimSpace(rawPath), "/api/media/")
	rel = strings.TrimPrefix(rel, "/")
	if rel == "" {
		return "", fmt.Errorf("media path is required")
	}
	return s.safeMediaPath(rel)
}

func (s *Service) safeMediaPath(relPath string) (string, error) {
	root := filepath.Clean(strings.TrimSpace(s.mediaDir))
	if root == "" {
		return "", fmt.Errorf("media dir is empty")
	}
	absRoot, err := filepath.Abs(root)
	if err != nil {
		return "", err
	}
	target := filepath.Clean(filepath.Join(absRoot, filepath.FromSlash(relPath)))
	if target != absRoot && !strings.HasPrefix(target, absRoot+string(os.PathSeparator)) {
		return "", fmt.Errorf("invalid media path")
	}
	return target, nil
}

func decodeMediaBase64(raw string) ([]byte, string, error) {
	value := strings.TrimSpace(raw)
	mime := ""
	if strings.HasPrefix(value, "data:") {
		if comma := strings.Index(value, ","); comma >= 0 {
			header := value[5:comma]
			if semi := strings.Index(header, ";"); semi >= 0 {
				mime = strings.TrimSpace(header[:semi])
			} else {
				mime = strings.TrimSpace(header)
			}
			value = value[comma+1:]
		}
	}
	value = strings.NewReplacer("\r", "", "\n", "", "\t", "", " ", "").Replace(value)
	decoded, err := base64.StdEncoding.DecodeString(value)
	if err != nil {
		decoded, err = base64.RawStdEncoding.DecodeString(value)
	}
	if err != nil {
		decoded, err = base64.URLEncoding.DecodeString(value)
	}
	if err != nil {
		return nil, "", err
	}
	if mime == "" {
		mime = http.DetectContentType(decoded)
	}
	return decoded, mime, nil
}

func mediaKindForMessage(messageType int32, mime string) string {
	switch {
	case strings.HasPrefix(strings.ToLower(strings.TrimSpace(mime)), "image/"):
		return "image"
	case strings.HasPrefix(strings.ToLower(strings.TrimSpace(mime)), "audio/"):
		return "voice"
	case strings.HasPrefix(strings.ToLower(strings.TrimSpace(mime)), "video/"):
		return "video"
	}
	switch messageType {
	case 3:
		return "image"
	case 34:
		return "voice"
	case 43, 62:
		return "video"
	case 47:
		return "emoji"
	case 48:
		return "location"
	case 49:
		return "file"
	default:
		return "file"
	}
}

func mediaExtension(kind string, mime string, name string) string {
	if ext := strings.ToLower(filepath.Ext(strings.TrimSpace(name))); ext != "" && len(ext) <= 12 {
		return ext
	}
	switch strings.ToLower(strings.TrimSpace(mime)) {
	case "image/jpeg":
		return ".jpg"
	case "image/png":
		return ".png"
	case "image/gif":
		return ".gif"
	case "image/webp":
		return ".webp"
	case "audio/amr":
		return ".amr"
	case "audio/mpeg":
		return ".mp3"
	case "audio/ogg":
		return ".ogg"
	case "video/mp4":
		return ".mp4"
	}
	switch strings.ToLower(strings.TrimSpace(kind)) {
	case "image":
		return ".jpg"
	case "voice":
		return ".amr"
	case "video":
		return ".mp4"
	default:
		return ".bin"
	}
}

func safePathPart(value string) string {
	value = strings.TrimSpace(value)
	if value == "" {
		return "unknown"
	}
	var b strings.Builder
	for _, ch := range value {
		if ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z' || ch >= '0' && ch <= '9' || ch == '-' || ch == '_' || ch == '.' {
			b.WriteRune(ch)
			continue
		}
		b.WriteByte('_')
	}
	out := strings.Trim(b.String(), "._")
	if out == "" {
		return "unknown"
	}
	return out
}
