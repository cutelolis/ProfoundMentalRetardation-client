package autismclient.util.custommenu;

import autismclient.api.custommenu.CustomMenuEvent;
import autismclient.api.custommenu.CustomMenuSubmitResult;
import autismclient.util.macro.CustomMenuAction;
import autismclient.util.macro.CustomMenuActionSupport;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundShowDialogPacket;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.input.TextInput;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class VanillaDialogAdapterTest {
    @org.junit.jupiter.api.BeforeAll
    static void bootstrapMinecraftRegistries() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void sparkLoginRegistrationBuildsDirectCustomClickPacket() {
        TextInput.MultilineOptions multiline = new TextInput.MultilineOptions(Optional.of(2), Optional.of(40));
        List<Input> inputs = List.of(
            new Input("password", new TextInput(200, Component.literal("Password"), true, "", 64, Optional.empty())),
            new Input("confirm_password", new TextInput(200, Component.literal("Confirm"), true, "", 64, Optional.of(multiline)))
        );
        ActionButton register = button("Register", "register_submit");
        ActionButton cancel = button("Cancel", "register_cancel");
        MultiActionDialog dialog = new MultiActionDialog(common("Register", inputs),
            List.of(register, cancel), Optional.empty(), 1);

        VanillaDialogAdapter adapter = new VanillaDialogAdapter();
        CustomMenuEvent event = adapter.inspectInbound(
            new ClientboundShowDialogPacket(Holder.direct(dialog)), "CONFIGURATION");
        assertEquals(CustomMenuEvent.Type.OPEN, event.type());
        assertEquals(List.of("password", "confirm_password"),
            event.snapshot().inputs().stream().map(input -> input.key()).toList());

        CustomMenuAction action = new CustomMenuAction();
        action.fieldValues.add("{secret.password}");
        CustomMenuActionSupport.Prepared prepared =
            CustomMenuActionSupport.prepare(action, event.snapshot(), ignored -> "secret123");
        assertTrue(prepared.success());
        assertEquals("secret123", prepared.submission().values().get("password"));
        assertEquals("secret123", prepared.submission().values().get("confirm_password"));
        CustomMenuSubmitResult result = adapter.submit(event.snapshot(), prepared.submission());

        assertTrue(result.success());
        assertEquals(1, result.packets().size());
        assertInstanceOf(ServerboundCustomClickActionPacket.class, result.packets().getFirst());
    }

    @Test
    void sparkResetContinueSelectsTheOnlyPositiveAction() {
        MultiActionDialog dialog = new MultiActionDialog(common("Account Reset", List.of()),
            List.of(button("Continue", "reset_continue")), Optional.empty(), 1);
        CustomMenuEvent event = new VanillaDialogAdapter().inspectInbound(
            new ClientboundShowDialogPacket(Holder.direct(dialog)), "CONFIGURATION");
        CustomMenuActionSupport.Prepared prepared = CustomMenuActionSupport.prepare(
            new CustomMenuAction(), event.snapshot(), ignored -> "unused");
        assertTrue(prepared.success());
        assertEquals("sparklogin:reset_continue", prepared.submission().button().actionId());
    }

    private static CommonDialogData common(String title, List<Input> inputs) {
        return new CommonDialogData(Component.literal(title), Optional.empty(), false, false,
            DialogAction.WAIT_FOR_RESPONSE, List.of(), inputs);
    }

    private static ActionButton button(String label, String id) {
        return new ActionButton(new CommonButtonData(Component.literal(label), 150),
            Optional.of(new CustomAll(Identifier.fromNamespaceAndPath("sparklogin", id), Optional.empty())));
    }
}
