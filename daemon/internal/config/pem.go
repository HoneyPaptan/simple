package config

import (
	"encoding/pem"
)

func pemEncode(typ string, der []byte) []byte {
	return pem.EncodeToMemory(&pem.Block{Type: typ, Bytes: der})
}

func pemDecodeCert(b []byte) []byte {
	block, _ := pem.Decode(b)
	if block == nil {
		return nil
	}
	return block.Bytes
}
