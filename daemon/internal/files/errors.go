package files

import "errors"

var (
	errBadRequest   = errors.New("bad_request")
	errNotFound     = errors.New("not_found")
	errDenied       = errors.New("denied")
	errBinary       = errors.New("binary")
	errTooLarge     = errors.New("too_large")
	errInternal     = errors.New("internal")
	errExists       = errors.New("exists")
	errInvalidName  = errors.New("invalid_name")
	errOutsideHome  = errors.New("outside_home")
)

func ErrKind(err error) string {
	switch {
	case errors.Is(err, errNotFound):
		return "not_found"
	case errors.Is(err, errDenied):
		return "denied"
	case errors.Is(err, errBinary):
		return "binary"
	case errors.Is(err, errTooLarge):
		return "too_large"
	case errors.Is(err, errExists):
		return "exists"
	case errors.Is(err, errInvalidName):
		return "invalid_name"
	case errors.Is(err, errOutsideHome):
		return "outside_home"
	case errors.Is(err, errBadRequest):
		return "bad_request"
	default:
		return "internal"
	}
}
