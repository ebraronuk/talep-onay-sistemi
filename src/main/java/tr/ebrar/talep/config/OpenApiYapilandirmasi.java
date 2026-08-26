package tr.ebrar.talep.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiYapilandirmasi {

    private static final String GUVENLIK_SEMASI = "bearer-jwt";

    @Bean
    OpenAPI apiTanimi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Kurumsal Talep ve Onay Yonetim Sistemi")
                        .version("v1")
                        .description("""
                                Personel talep acar, birim amiri onaylar veya reddeder, her degisiklik
                                denetim izine yazilir.

                                Kullanim: once POST /api/kimlik/giris ile token alin, sonra sag ustteki
                                Authorize dugmesine tokeni yapistirin.
                                """))
                // Authorize dugmesi olmadan Swagger uzerinden hicbir ucu deneyemezsiniz,
                // cunku hepsi token istiyor.
                .components(new Components().addSecuritySchemes(GUVENLIK_SEMASI,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(GUVENLIK_SEMASI));
    }
}
