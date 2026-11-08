package xpizzax.emoticons.animation;
import net.minecraft.entity.LivingEntity;


public class ActionPlayback
{
    public Clip action;
    public ActionConfig config;

    private int fade;
    private float ticks;
    private int duration;
    private double speed = 1;
    private double renderSpeed = 1;

    private boolean looping;
    private Fade fading = Fade.FINISHED;
    public boolean playing = true;
    public int priority;

    public ActionPlayback(Clip action, ActionConfig config, boolean looping)
    {
        this.action = action;
        this.config = config;
        this.duration = action.getLengthInTicks();
        this.looping = looping;
        this.setSpeed(1);
        this.renderSpeed = this.speed;
    }

    public ActionPlayback(Clip action, ActionConfig config, boolean looping, int priority)
    {
        this(action, config, looping);
        this.priority = priority;
    }

    /* Action playback control methods */

    public boolean isLooping()
    {
        return this.looping && this.config.loop;
    }

    /**
     * Restart the clip at the correct playback edge and clear its previous fade.
     */
    public void rewind()
    {
        // Looping clips wrap from the appropriate edge; one-shot clips must
        // also restart at their first frame when an interaction repeats.
        this.ticks = this.speed < 0 ? this.duration : 0;
        this.playing = true;
        this.stopFade();
        this.renderSpeed = this.speed;
    }

    /**
     * Whether this action playback finished fading
     */
    public boolean finishedFading()
    {
        return this.fading != Fade.FINISHED && this.fade <= 0;
    }

    public boolean isFadingModeOut()
    {
        return this.fading == Fade.OUT;
    }

    public boolean isFadingModeIn()
    {
        return this.fading == Fade.IN;
    }

    /**
     * Whether this action playback is fading
     */
    public boolean isFading()
    {
        return this.fading != Fade.FINISHED && this.fade > 0;
    }

    /**
     * Start fading out
     */
    public void fadeOut()
    {
        this.fade = (int) this.config.fade;
        this.fading = Fade.OUT;
    }

    /**
     * Start fading in
     */
    public void fadeIn()
    {
        this.fade = (int) this.config.fade;
        this.fading = Fade.IN;
    }

    /**
     * Reset fading
     */
    public void stopFade()
    {
        this.fade = 0;
        this.fading = Fade.FINISHED;
    }

    public void resetFade()
    {
        this.fade = 0;
        this.fading = Fade.FINISHED;
    }

    public int getFade()
    {
        return this.fade;
    }

    /**
     * Calculate fade factor with given partial ticks
     *
     * A value closer to 1 means started fading, meanwhile closer to 0
     * is almost finished fading.
     */
    public float getFadeFactor(float transition)
    {
        float factor = (this.fade - transition) / this.config.fade;

        return this.fading == Fade.OUT ? factor : 1 - factor;
    }

    /**
     * Set speed of an action playback
     */
    public void setSpeed(double speed)
    {
        this.speed = speed * this.config.speed;
    }

    /* Update methods */

    public void update()
    {
        if (this.fading != Fade.FINISHED && this.fade > 0)
        {
            this.fade--;
        }

        if (!this.playing) return;

        // Complete the interval that was already rendered before adopting a
        // changed direction. At delta=0 the new pose equals the old delta=1,
        // even when forward/backward direction changes immediately.
        double previousSpeed = this.renderSpeed;
        this.ticks += previousSpeed;
        this.renderSpeed = this.speed;

        boolean looping = this.looping;

        if (!this.config.loop)
        {
             looping = false;
        }

        if (!looping && this.fading != Fade.OUT && this.ticks >= this.duration)
        {
            this.fadeOut();
        }

        if (looping)
        {
            if (this.ticks >= this.duration && previousSpeed > 0)
            {
                this.ticks -= this.duration;
                this.ticks += this.config.tick;
            }
            else if (this.ticks < 0 && previousSpeed < 0)
            {
                this.ticks = this.duration + this.ticks;
                this.ticks -= this.config.tick;
            }
        }
    }

    public float getTick(float transition)
    {
        boolean looping = this.looping;

        if (!this.config.loop)
        {
            looping = false;
        }

        float ticks = this.ticks + (float) (transition * this.renderSpeed);

        if (looping)
        {
            if (ticks >= this.duration)
            {
                ticks -= this.duration;
            }
            else if (ticks < 0)
            {
                ticks = this.duration + ticks;
            }
        }

        return ticks;
    }

    public void apply(LivingEntity target, Rig armature, float transition, float blend, boolean skipInitial)
    {
        float tick = this.getTick(transition);

        armature.apply(target, this.action, tick, blend, transition, skipInitial);
    }

    public void postApply(LivingEntity target, Rig armature, float transition)
    {
        float tick = this.getTick(transition);

        armature.postApply(target, this.action, tick, transition);
    }

    public static enum Fade
    {
        OUT, FINISHED, IN
    }
}
