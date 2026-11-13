package config

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/sha256"
	"crypto/subtle"
	"crypto/tls"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"math/big"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"sh.simple/simpled/internal/appdirs"
	"sh.simple/simpled/internal/hcsock"
)

var Version = "0.1.0"

type Config struct {
	Port     int             `json:"port"`
	Lan      bool            `json:"lan"`
	Webhooks []Webhook       `json:"webhooks,omitempty"`
}

type Webhook struct {
	ID     string   `json:"id"`
	URL    string   `json:"url"`
	Secret string   `json:"secret"`
	Kinds  []string `json:"kinds"`
}

type Device struct {
	ID          string `json:"id"`
	Name        string `json:"name"`
	Model       string `json:"model"`
	TokenSHA256 string `json:"token_sha256"`
	Created     int64  `json:"created"`
	LastSeen    int64  `json:"last_seen"`
}

type Store struct {
	Home       string
	ConfigDir  string
	StateDir   string
	RuntimeDir string
	ControlSock string
	HCSock      string
	HerdrSock   string

	mu sync.Mutex
}

func NewStore(home, runtimeDir string) *Store {
	if home == "" {
		home, _ = os.UserHomeDir()
	}
	if runtimeDir == "" {
		runtimeDir = appdirs.RuntimeDir(home)
	}
	return &Store{
		Home:        home,
		ConfigDir:   appdirs.ConfigDir(home),
		StateDir:    appdirs.StateDir(home),
		RuntimeDir:  runtimeDir,
		ControlSock: appdirs.ControlSocket(home),
		HCSock:      hcsock.Resolve(),
		HerdrSock:   "",
	}
}

func (s *Store) HerdrSocket() string {
	if s.HerdrSock != "" {
		return s.HerdrSock
	}
	return hcsock.HerdrSocket()
}

func (s *Store) EnsureDirs() error {
	for _, d := range []string{s.ConfigDir, s.StateDir} {
		if err := os.MkdirAll(d, 0o700); err != nil {
			return err
		}
	}
	return nil
}

func (s *Store) SaveConfig(c Config) error {
	if err := s.EnsureDirs(); err != nil {
		return err
	}
	b, err := json.MarshalIndent(c, "", "  ")
	if err != nil {
		return err
	}
	return atomicWrite(filepath.Join(s.ConfigDir, "config.json"), b, 0o600)
}

func (s *Store) LoadConfig() (Config, error) {
	var c Config
	c.Port = 47420
	c.Lan = false
	path := filepath.Join(s.ConfigDir, "config.json")
	b, err := os.ReadFile(path)
	if err != nil {
		if os.IsNotExist(err) {
			return c, nil
		}
		return c, err
	}
	if err := json.Unmarshal(b, &c); err != nil {
		return c, err
	}
	if c.Port == 0 {
		c.Port = 47420
	}
	return c, nil
}

func (s *Store) HostIDPath() string {
	return filepath.Join(s.ConfigDir, "host.json")
}

type hostFile struct {
	ID string `json:"id"`
}

func (s *Store) HostID() (string, error) {
	path := s.HostIDPath()
	b, err := os.ReadFile(path)
	if err == nil {
		var h hostFile
		if json.Unmarshal(b, &h) == nil && h.ID != "" {
			return h.ID, nil
		}
	}
	id, err := randomID("h_")
	if err != nil {
		return "", err
	}
	if err := s.EnsureDirs(); err != nil {
		return "", err
	}
	out, _ := json.Marshal(hostFile{ID: id})
	if err := atomicWrite(path, out, 0o600); err != nil {
		return "", err
	}
	return id, nil
}

func (s *Store) HostName() string {
	h, err := os.Hostname()
	if err != nil || h == "" {
		return "simple"
	}
	return h
}

func (s *Store) CertPaths() (cert, key string) {
	return filepath.Join(s.ConfigDir, "cert.pem"), filepath.Join(s.ConfigDir, "key.pem")
}

func (s *Store) TLSConfig() (*tls.Config, string, error) {
	certPath, keyPath := s.CertPaths()
	if err := s.EnsureDirs(); err != nil {
		return nil, "", err
	}
	if _, err := os.Stat(certPath); os.IsNotExist(err) {
		if err := s.generateCert(certPath, keyPath); err != nil {
			return nil, "", err
		}
	}
	cert, err := tls.LoadX509KeyPair(certPath, keyPath)
	if err != nil {
		return nil, "", err
	}
	fp, err := fingerprintCertFile(certPath)
	if err != nil {
		return nil, "", err
	}
	return &tls.Config{
		MinVersion: tls.VersionTLS13,
		Certificates: []tls.Certificate{cert},
	}, fp, nil
}

func fingerprintCertFile(certPath string) (string, error) {
	b, err := os.ReadFile(certPath)
	if err != nil {
		return "", err
	}
	block := pemDecodeCert(b)
	if block == nil {
		return "", errors.New("no certificate")
	}
	sum := sha256.Sum256(block)
	return hex.EncodeToString(sum[:]), nil
}

func (s *Store) Fingerprint() (string, error) {
	certPath, _ := s.CertPaths()
	if _, err := os.Stat(certPath); os.IsNotExist(err) {
		if _, _, err := s.TLSConfig(); err != nil {
			return "", err
		}
	}
	return fingerprintCertFile(certPath)
}

func (s *Store) generateCert(certPath, keyPath string) error {
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		return err
	}
	serial, err := rand.Int(rand.Reader, new(big.Int).Lsh(big.NewInt(1), 128))
	if err != nil {
		return err
	}
	tmpl := x509.Certificate{
		SerialNumber: serial,
		Subject:      pkix.Name{CommonName: "simpled"},
		NotBefore:    time.Now(),
		NotAfter:     time.Now().Add(10 * 365 * 24 * time.Hour),
		KeyUsage:     x509.KeyUsageDigitalSignature,
		ExtKeyUsage:  []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth},
	}
	der, err := x509.CreateCertificate(rand.Reader, &tmpl, &tmpl, &key.PublicKey, key)
	if err != nil {
		return err
	}
	certPEM := pemEncode("CERTIFICATE", der)
	keyDER, err := x509.MarshalECPrivateKey(key)
	if err != nil {
		return err
	}
	keyPEM := pemEncode("EC PRIVATE KEY", keyDER)
	if err := atomicWrite(certPath, certPEM, 0o600); err != nil {
		return err
	}
	return atomicWrite(keyPath, keyPEM, 0o600)
}

func (s *Store) DevicesPath() string {
	return filepath.Join(s.ConfigDir, "devices.json")
}

func (s *Store) LoadDevices() ([]Device, error) {
	path := s.DevicesPath()
	b, err := os.ReadFile(path)
	if err != nil {
		if os.IsNotExist(err) {
			return nil, nil
		}
		return nil, err
	}
	var list []Device
	if err := json.Unmarshal(b, &list); err != nil {
		return nil, err
	}
	return list, nil
}

func (s *Store) SaveDevices(list []Device) error {
	if err := s.EnsureDirs(); err != nil {
		return err
	}
	b, err := json.MarshalIndent(list, "", "  ")
	if err != nil {
		return err
	}
	return atomicWrite(s.DevicesPath(), b, 0o600)
}

func (s *Store) AddDevice(name, model, token string) (Device, error) {
	sum := sha256.Sum256([]byte(token))
	id, err := randomID("d_")
	if err != nil {
		return Device{}, err
	}
	now := time.Now().UnixMilli()
	d := Device{
		ID:          id,
		Name:        name,
		Model:       model,
		TokenSHA256: hex.EncodeToString(sum[:]),
		Created:     now,
		LastSeen:    now,
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	list, err := s.LoadDevices()
	if err != nil {
		return Device{}, err
	}
	list = append(list, d)
	if err := s.SaveDevices(list); err != nil {
		return Device{}, err
	}
	return d, nil
}

func (s *Store) RevokeDevice(id string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	list, err := s.LoadDevices()
	if err != nil {
		return err
	}
	out := list[:0]
	found := false
	for _, d := range list {
		if d.ID == id {
			found = true
			continue
		}
		out = append(out, d)
	}
	if !found {
		return os.ErrNotExist
	}
	return s.SaveDevices(out)
}

func (s *Store) FindDeviceByTokenSHA256(hash string) (*Device, error) {
	hash = strings.TrimSpace(strings.ToLower(hash))
	if hash == "" {
		return nil, nil
	}
	want, err := hex.DecodeString(hash)
	if err != nil || len(want) != sha256.Size {
		return nil, nil
	}
	list, err := s.LoadDevices()
	if err != nil {
		return nil, err
	}
	for i := range list {
		got, err := hex.DecodeString(list[i].TokenSHA256)
		if err != nil || len(got) != len(want) {
			continue
		}
		if subtle.ConstantTimeCompare(got, want) == 1 {
			return &list[i], nil
		}
	}
	return nil, nil
}

func (s *Store) FindDeviceByToken(token string) (*Device, error) {
	wantSum := sha256.Sum256([]byte(token))
	list, err := s.LoadDevices()
	if err != nil {
		return nil, err
	}
	for i := range list {
		got, err := hex.DecodeString(list[i].TokenSHA256)
		if err != nil || len(got) != len(wantSum) {
			continue
		}
		if subtle.ConstantTimeCompare(got, wantSum[:]) == 1 {
			return &list[i], nil
		}
	}
	return nil, nil
}

func (s *Store) TouchDevice(id string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	list, err := s.LoadDevices()
	if err != nil {
		return err
	}
	now := time.Now().UnixMilli()
	for i := range list {
		if list[i].ID == id {
			list[i].LastSeen = now
			return s.SaveDevices(list)
		}
	}
	return os.ErrNotExist
}

func NewToken() (string, error) {
	var b [32]byte
	if _, err := rand.Read(b[:]); err != nil {
		return "", err
	}
	return base64.RawURLEncoding.EncodeToString(b[:]), nil
}

func RandomID(prefix string) (string, error) {
	return randomID(prefix)
}

func randomID(prefix string) (string, error) {
	var b [8]byte
	if _, err := rand.Read(b[:]); err != nil {
		return "", err
	}
	return prefix + hex.EncodeToString(b[:]), nil
}

func atomicWrite(path string, data []byte, mode os.FileMode) error {
	dir := filepath.Dir(path)
	tmp, err := os.CreateTemp(dir, ".tmp-*")
	if err != nil {
		return err
	}
	tmpName := tmp.Name()
	defer os.Remove(tmpName)
	if _, err := tmp.Write(data); err != nil {
		tmp.Close()
		return err
	}
	if err := tmp.Chmod(mode); err != nil {
		tmp.Close()
		return err
	}
	if err := tmp.Close(); err != nil {
		return err
	}
	return os.Rename(tmpName, path)
}
