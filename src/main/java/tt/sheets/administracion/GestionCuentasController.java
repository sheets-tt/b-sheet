package tt.sheets.administracion;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.Patch;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tt.sheets.administracion.application.GestionarCuentas;
import tt.sheets.administracion.domain.ContrasenaInvalidaException;
import tt.sheets.administracion.domain.CorreoEnUsoException;
import tt.sheets.administracion.domain.CuentaNoEncontradaException;
import tt.sheets.administracion.domain.CuentaPropiaException;
import tt.sheets.administracion.domain.CuentaResumen;
import tt.sheets.administracion.domain.DatosCuenta;
import tt.sheets.administracion.domain.UltimoAdministradorException;
import tt.sheets.auth.ErrorResponse;
import tt.sheets.auth.infrastructure.CuentaVigenteJwtAuthenticationFactory;
import tt.sheets.cuenta.Rol;

import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Alta y cambios de cuentas desde la web, solo para ADMINISTRADOR (RN-01: no hay registro público; RF-26).
 * Errores como {"message": "..."}: 400 datos inválidos, 404 id inexistente, 409 correo en uso o cambio que
 * dejaría al propio administrador sin acceso o al sistema sin administradores activos. Las cuentas no se borran:
 * se desactivan. Los logs de auditoría registran la cuenta afectada y quién hizo el cambio, solo por id.
 */
@Controller("/cuentas")
@Secured("ADMINISTRADOR")
public class GestionCuentasController {

    private static final Logger LOG = LoggerFactory.getLogger(GestionCuentasController.class);

    private final GestionarCuentas gestionar;

    public GestionCuentasController(GestionarCuentas gestionar) {
        this.gestionar = gestionar;
    }

    @Post
    @ExecuteOn(TaskExecutors.BLOCKING)
    public HttpResponse<CuentaResumenResponse> crear(@Body @Valid CrearCuentaRequest request,
                                                    Authentication authentication) {
        CuentaResumen cuenta = gestionar.crear(datos(request.correo(), request.nombreCompleto(), request.rol()),
                request.contrasena());
        LOG.info("cuenta creada: {} por {}", cuenta.id(), actor(authentication));
        return HttpResponse.created(CuentaResumenResponse.from(cuenta));
    }

    @Put("/{id}")
    @ExecuteOn(TaskExecutors.BLOCKING)
    public CuentaResumenResponse editar(UUID id, @Body @Valid EditarCuentaRequest request,
                                        Authentication authentication) {
        CuentaResumen cuenta = gestionar.editar(id, datos(request.correo(), request.nombreCompleto(), request.rol()),
                request.contrasena(), authentication.getName());
        LOG.info("cuenta editada: {} por {}", id, actor(authentication));
        return CuentaResumenResponse.from(cuenta);
    }

    @Patch("/{id}/desactivar")
    @ExecuteOn(TaskExecutors.BLOCKING)
    public CuentaResumenResponse desactivar(UUID id, Authentication authentication) {
        CuentaResumen cuenta = gestionar.desactivar(id, authentication.getName());
        LOG.info("cuenta desactivada: {} por {}", id, actor(authentication));
        return CuentaResumenResponse.from(cuenta);
    }

    @Patch("/{id}/activar")
    @ExecuteOn(TaskExecutors.BLOCKING)
    public CuentaResumenResponse activar(UUID id, Authentication authentication) {
        CuentaResumen cuenta = gestionar.activar(id);
        LOG.info("cuenta activada: {} por {}", id, actor(authentication));
        return CuentaResumenResponse.from(cuenta);
    }

    /** Id de quien hace el cambio (el token ya se validó contra la BD); nunca el correo. */
    private static Object actor(Authentication authentication) {
        return CuentaVigenteJwtAuthenticationFactory.idCuenta(authentication).map(Object.class::cast).orElse("?");
    }

    private static DatosCuenta datos(String correo, String nombreCompleto, String rol) {
        return new DatosCuenta(correo, nombreCompleto, Rol.valueOf(rol)); // rol ya validado con @Pattern
    }

    @Error(exception = ConstraintViolationException.class)
    HttpResponse<ErrorResponse> invalido(HttpRequest<?> request, ConstraintViolationException e) {
        // Solo los mensajes propios (sin rutas internas ni el valor recibido, que podría ser la contraseña).
        String mensaje = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .sorted()
                .distinct()
                .collect(Collectors.joining("; "));
        return HttpResponse.badRequest(new ErrorResponse(mensaje));
    }

    @Error(exception = ContrasenaInvalidaException.class)
    HttpResponse<ErrorResponse> contrasenaInvalida(HttpRequest<?> request, ContrasenaInvalidaException e) {
        return HttpResponse.badRequest(new ErrorResponse(e.getMessage()));
    }

    @Error(exception = CuentaNoEncontradaException.class)
    HttpResponse<ErrorResponse> noEncontrada(HttpRequest<?> request, CuentaNoEncontradaException e) {
        return HttpResponse.notFound(new ErrorResponse(e.getMessage()));
    }

    @Error(exception = CorreoEnUsoException.class)
    HttpResponse<ErrorResponse> correoEnUso(HttpRequest<?> request, CorreoEnUsoException e) {
        return HttpResponse.<ErrorResponse>status(HttpStatus.CONFLICT).body(new ErrorResponse(e.getMessage()));
    }

    @Error(exception = CuentaPropiaException.class)
    HttpResponse<ErrorResponse> cuentaPropia(HttpRequest<?> request, CuentaPropiaException e) {
        return HttpResponse.<ErrorResponse>status(HttpStatus.CONFLICT).body(new ErrorResponse(e.getMessage()));
    }

    @Error(exception = UltimoAdministradorException.class)
    HttpResponse<ErrorResponse> ultimoAdministrador(HttpRequest<?> request, UltimoAdministradorException e) {
        return HttpResponse.<ErrorResponse>status(HttpStatus.CONFLICT).body(new ErrorResponse(e.getMessage()));
    }
}
