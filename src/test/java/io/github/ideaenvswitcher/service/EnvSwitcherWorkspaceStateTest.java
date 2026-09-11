package io.github.ideaenvswitcher.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EnvSwitcherWorkspaceStateTest {

    @Test
    void roundTripsLastProfileNameViaStateBean() {
        EnvSwitcherWorkspaceState.State state = new EnvSwitcherWorkspaceState.State();
        assertNull(state.lastProfileName);

        state.lastProfileName = "staging";
        assertEquals("staging", state.lastProfileName);

        EnvSwitcherWorkspaceState holder = new EnvSwitcherWorkspaceState();
        holder.loadState(state);
        assertEquals("staging", holder.getLastProfileName());

        holder.setLastProfileName("prod");
        assertEquals("prod", holder.getState().lastProfileName);
    }
}
