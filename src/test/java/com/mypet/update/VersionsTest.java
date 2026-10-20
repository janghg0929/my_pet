package com.mypet.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionsTest {
	@Test
	void comparesLikePythonParseVersion() {
		assertTrue(Versions.isNewer("1.0.4", "1.0.3"));
		assertTrue(Versions.isNewer("1.10.0", "1.9.9"));
		assertTrue(Versions.isNewer("2", "1.9.9"));
		assertFalse(Versions.isNewer("1.0.3", "1.0.3"));
		assertFalse(Versions.isNewer("1.0.2", "1.0.3"));
		assertTrue(Versions.isNewer("1.0.0", "0.0.0-dev"));
	}

	@Test
	void normalizesTags() {
		assertEquals("1.2.3", Versions.normalize("v1.2.3"));
		assertEquals("1.2.3", Versions.normalize("﻿ 1.2.3 \n"));
		assertNull(Versions.normalize("  "));
	}

	@Test
	void extractsTagFromReleaseRedirect() {
		assertEquals("v1.2.3", UpdateService.extractTag("https://github.com/janghg0929/my_pet/releases/tag/v1.2.3"));
		assertEquals("v1.2.3", UpdateService.extractTag("/owner/repo/releases/tag/v1.2.3?x=1"));
		assertNull(UpdateService.extractTag("https://github.com/janghg0929/my_pet/releases"));
	}
}
