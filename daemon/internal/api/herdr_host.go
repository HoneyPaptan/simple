package api

import (
	"context"
)

func (s *Server) herdrVersion() *string {
	if s.Herdr == nil {
		return nil
	}
	return s.Herdr.Version(context.Background())
}
