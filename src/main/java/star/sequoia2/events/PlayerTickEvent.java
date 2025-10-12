package star.sequoia2.events;

import com.collarmc.pounce.Cancelable;
import com.collarmc.pounce.EventInfo;
import com.collarmc.pounce.Preference;

@EventInfo(preference = Preference.CALLER)
public record PlayerTickEvent() implements Cancelable {
}