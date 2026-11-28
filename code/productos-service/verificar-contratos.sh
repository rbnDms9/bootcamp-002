#!/usr/bin/env bash
# Verifica que las copias de los contratos en productos-service sean idénticas
# a los contratos originales de catalogo-service y descuento-service.
# Uso (desde code/productos-service):  ./verificar-contratos.sh
# En CI: si falla, el pipeline se detiene antes de compilar.
set -euo pipefail
cd "$(dirname "$0")"

estado=0
verificar() {
  local original="$1" copia="$2"
  if diff -q "$original" "$copia" >/dev/null; then
    echo "OK     $(basename "$copia")"
  else
    echo "DIFERENTE $(basename "$copia"): copia con  cp $original $copia"
    estado=1
  fi
}

verificar ../catalogo-service/src/main/resources/openapi/catalog-api.yaml  src/main/resources/openapi/catalog-api.yaml
verificar ../descuento-service/src/main/resources/openapi/discount-api.yaml src/main/resources/openapi/discount-api.yaml
exit $estado
