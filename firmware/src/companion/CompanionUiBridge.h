#pragma once

namespace companion {
class SessionEngine;
struct TicketState;
void showFocus(SessionEngine& session);
void showTicket(TicketState& ticket);
void showHome();
void hideTicketIfVisible();
void refreshFocus();
}  // namespace companion
