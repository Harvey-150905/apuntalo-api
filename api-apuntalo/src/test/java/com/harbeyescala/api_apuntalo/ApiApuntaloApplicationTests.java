package com.harbeyescala.api_apuntalo;

import org.junit.jupiter.api.Test;
import com.harbeyescala.api_apuntalo.service.AuthService;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiApuntaloApplicationTests {

	@Test
	void usernameIsNormalizedForGlobalLookup() {
		assertEquals("usuario", AuthService.normalizeUsername("  UsUaRiO  "));
	}

}
