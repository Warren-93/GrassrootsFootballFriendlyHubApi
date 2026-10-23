package com.gffh.api.repository.callback;

import com.gffh.api.model.MemberInvite;
import com.gffh.api.model.PlatformAdmin;
import com.gffh.api.model.User;
import com.gffh.api.repository.MemberInviteRepository;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Stores email addresses in the form the repositories look them up by:
 * lower-cased for users and admins, trimmed and lower-cased for invites. The
 * matching lookups are {@code UserRepository.findByEmail},
 * {@code PlatformAdminRepository.findByEmail} and the email methods on
 * {@link MemberInviteRepository}.
 */
@Component
public class EmailNormalisingCallback implements BeforeConvertCallback<Object> {

    @Override
    public Object onBeforeConvert(Object entity, String collection) {
        if (entity instanceof User user && user.getEmail() != null) {
            user.setEmail(user.getEmail().toLowerCase(Locale.ROOT));
        } else if (entity instanceof PlatformAdmin admin && admin.getEmail() != null) {
            admin.setEmail(admin.getEmail().toLowerCase(Locale.ROOT));
        } else if (entity instanceof MemberInvite invite) {
            invite.setEmail(MemberInviteRepository.normalised(invite.getEmail()));
        }
        return entity;
    }
}
