#pragma once

namespace companion {
class SessionEngine;
struct TicketState;
void showFocus(SessionEngine& session);
void showTicket(TicketState& ticket);
void showHome();
void hideTicketIfVisible();
// Re-render Home when authoritative device state changes without navigating
// away from the activity the user is currently using.
void refreshHomeIfVisible();
void refreshFocus();
}  // namespace companion
