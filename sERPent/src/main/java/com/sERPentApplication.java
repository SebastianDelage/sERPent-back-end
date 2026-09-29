package com;

import com.empresa.serpent.shared.db.MigrateOnlyMode;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class sERPentApplication {

    public static void main(String[] args) {
        // MODO "SOLO MIGRAR", que usa el instalador al actualizar: migra, informa con que
        // resultado, y sale con un codigo propio. Ver MigrateOnlyMode: existe para que una
        // migracion fallida se detecte ANTES de decirle a nadie que la actualizacion salio bien.
        if (MigrateOnlyMode.requested(args)) {
            System.exit(MigrateOnlyMode.run(sERPentApplication.class, args));
        }

        SpringApplication.run(sERPentApplication.class, args);
    }

}
