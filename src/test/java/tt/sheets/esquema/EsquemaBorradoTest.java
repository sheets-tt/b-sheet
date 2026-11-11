package tt.sheets.esquema;

import io.micronaut.context.ApplicationContext;
import io.micronaut.transaction.TransactionOperations;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Esquema de TT_BKN (réplica): analisis.id_arbol / id_zona eran ON DELETE SET NULL, pero chk_arbol_o_zona
 * exige que un análisis de corteza tenga árbol (y uno de follaje, zona). Borrar un árbol con análisis
 * fallaba con violación del CHECK. Con ON DELETE CASCADE se van sus análisis (y sus cromáticos).
 * Cada prueba trabaja en una transacción que se revierte: no altera los datos de db/demo.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EsquemaBorradoTest {

    private ApplicationContext context;
    private TransactionOperations<Connection> tx;

    @BeforeAll
    @SuppressWarnings("unchecked")
    void start() {
        context = ApplicationContext.run("test");
        tx = context.getBean(TransactionOperations.class);
    }

    @AfterAll
    void stop() {
        context.close();
    }

    private static int contar(Connection c, String sql, UUID id) throws Exception {
        try (PreparedStatement st = c.prepareStatement(sql)) {
            st.setObject(1, id);
            try (ResultSet rs = st.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static void ejecutar(Connection c, String sql, UUID id) throws Exception {
        try (PreparedStatement st = c.prepareStatement(sql)) {
            st.setObject(1, id);
            st.executeUpdate();
        }
    }

    @Test
    void borrarUnArbolBorraSusAnalisis() {
        UUID oyamel = UUID.fromString("b0000000-0000-0000-0000-000000000001"); // tiene e1 (corteza)
        tx.executeWrite(status -> {
            Connection c = status.getConnection();
            try {
                ejecutar(c, "DELETE FROM arbol WHERE id_arbol = ?", oyamel);
                assertEquals(0, contar(c, "SELECT count(*) FROM analisis WHERE id_arbol = ?", oyamel));
            } catch (Exception e) {
                throw new AssertionError("borrar un árbol con análisis falló: " + e.getMessage(), e);
            } finally {
                status.setRollbackOnly();
            }
            return null;
        });
    }

    @Test
    void borrarUnaZonaBorraSusAnalisisYCromaticos() {
        UUID lago = UUID.fromString("c0000000-0000-0000-0000-000000000001"); // tiene e3 y e4, con cromático
        tx.executeWrite(status -> {
            Connection c = status.getConnection();
            try {
                ejecutar(c, "DELETE FROM zona_follaje WHERE id_zona = ?", lago);
                assertEquals(0, contar(c, "SELECT count(*) FROM analisis WHERE id_zona = ?", lago));
                assertEquals(0, contar(c, "SELECT count(*) FROM analisis_cromatico WHERE id_analisis IN "
                        + "('e0000000-0000-0000-0000-000000000003','e0000000-0000-0000-0000-000000000004') "
                        + "AND ?::uuid IS NOT NULL", lago));
            } catch (Exception e) {
                throw new AssertionError("borrar una zona con análisis falló: " + e.getMessage(), e);
            } finally {
                status.setRollbackOnly();
            }
            return null;
        });
    }
}
