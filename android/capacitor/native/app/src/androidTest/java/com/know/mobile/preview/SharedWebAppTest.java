package com.know.mobile.preview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Device checks for the actual Capacitor WebView, not a desktop browser substitute. */
@RunWith(AndroidJUnit4.class)
public class SharedWebAppTest {
    private String evaluate(ActivityScenario<MainActivity> scenario, String script) throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();
        scenario.onActivity(activity -> activity.getBridge().getWebView().evaluateJavascript(script, value -> {
            result.set(value);
            ready.countDown();
        }));
        assertTrue("WebView evaluation timed out", ready.await(5, TimeUnit.SECONDS));
        return result.get();
    }

    private void until(ActivityScenario<MainActivity> scenario, String expression) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            if ("true".equals(evaluate(scenario, expression))) return;
            Thread.sleep(100);
        }
        throw new AssertionError("WebView condition failed: " + expression);
    }

    @Test
    public void rendersSharedWebTimelineWithExistingPalette() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            until(scenario, "document.body.innerText.includes('PostgreSQL indexes')");
            evaluate(scenario, "document.querySelector('button[aria-label=\"light theme\"]').click()");
            until(scenario, "getComputedStyle(document.documentElement).getPropertyValue('--accent').trim() === '#1660ec'");
            assertEquals("\"#1660ec\"", evaluate(scenario,
                "getComputedStyle(document.documentElement).getPropertyValue('--accent').trim()"));
            assertEquals("true", evaluate(scenario, "location.hostname === 'localhost'"));
        }
    }

    @Test
    public void nativeBackDismissesReactDialogBeforeNavigatingAway() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            until(scenario, "document.body.innerText.includes('PostgreSQL indexes')");
            evaluate(scenario, "[...document.querySelectorAll('button')].find(b=>b.textContent.includes('Categories')).click()");
            until(scenario, "document.body.innerText.includes('New category')");
            evaluate(scenario, "[...document.querySelectorAll('button')].find(b=>b.textContent.includes('New category')).click()");
            until(scenario, "Boolean(document.querySelector('dialog[open]'))");
            scenario.onActivity(activity -> activity.getOnBackPressedDispatcher().onBackPressed());
            until(scenario, "!document.querySelector('dialog[open]')");
            assertEquals("true", evaluate(scenario, "location.hash === '#categories'"));
        }
    }
}
