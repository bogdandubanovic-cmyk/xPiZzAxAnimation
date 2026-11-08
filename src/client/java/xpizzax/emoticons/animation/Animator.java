package xpizzax.emoticons.animation;
import net.minecraft.entity.LivingEntity;

import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Animator class
 * 
 * This class is responsible for applying currently running actions onto 
 * form (more specifically onto an armature).
 */
public class Animator
{
    /* Actions */
    public ActionPlayback idle;
    public ActionPlayback running;
    public ActionPlayback sprinting;
    public ActionPlayback crouching;
    public ActionPlayback crouchingIdle;
    public ActionPlayback dying;
    public ActionPlayback falling;

    public ActionPlayback jump1;
    public ActionPlayback jump2;
    public ActionPlayback swipe;
    public ActionPlayback hurt;
    public ActionPlayback land;
    public ActionPlayback shoot;
    public ActionPlayback consume;

    public ActionPlayback basePre;
    public ActionPlayback basePost;

    /* Action pipeline properties */
    public ActionPlayback active;
    public ActionPlayback lastActive;
    public List<ActionPlayback> actions = new ArrayList<>();

    public double prevX = Float.MAX_VALUE;
    public double prevZ = Float.MAX_VALUE;
    public double prevY;
    public double prevMY;
    public float prevHandSwing;

    /* States */
    public boolean wasOnGround = true;
    public int jumpingCounter;
    private final MotionGate motionGate = new MotionGate();
    private final GaitDirection gaitDirection = new GaitDirection();
    private final PrimaryActionBlend primaryBlend = new PrimaryActionBlend();

    private Rig model;

    public List<String> getActions()
    {
        return Arrays.asList(
            "idle", "running", "sprinting", "crouching", "crouching_idle", "dying", "falling",
            "swipe", "jump", "hurt", "land", "shoot", "consume", "base_pre", "base_post"
        );
    }

    public void setup(Rig model, ActionsConfig actions, boolean fade)
    {
        this.model = model;

        this.idle = this.createAction(this.idle, actions.getConfig("idle"), true);
        this.running = this.createAction(this.running, actions.getConfig("running"), true);
        this.sprinting = this.createAction(this.sprinting, actions.getConfig("sprinting"), true);
        this.crouching = this.createAction(this.crouching, actions.getConfig("crouching"), true);
        this.crouchingIdle = this.createAction(this.crouchingIdle, actions.getConfig("crouching_idle"), true);
        this.dying = this.createAction(this.dying, actions.getConfig("dying"), false);
        this.falling = this.createAction(this.falling, actions.getConfig("falling"), true);

        this.swipe = this.createAction(this.swipe, actions.getConfig("swipe"), false);
        this.jump1 = this.createAction(this.jump1, actions.getConfig("jump"), false, 2);
        this.jump2 = this.createAction(this.jump2, actions.getConfig("jump_alt"), false, 2);
        this.hurt = this.createAction(this.hurt, actions.getConfig("hurt"), false, 3);
        this.land = this.createAction(this.land, actions.getConfig("land"), false);
        this.shoot = this.createAction(this.shoot, actions.getConfig("shoot"), true);
        this.consume = this.createAction(this.consume, actions.getConfig("consume"), true);

        this.basePre = this.createAction(this.basePre, actions.getConfig("base_pre"), true);
        this.basePost = this.createAction(this.basePost, actions.getConfig("base_post"), true);

        this.primaryBlend.setup(this.idle, this.running, this.sprinting,
                this.crouching, this.crouchingIdle, this.dying, this.falling);

        if (!fade)
        {
            this.prevHandSwing = 0F;
            this.active = null;
            this.lastActive = null;
            this.setActiveAction(this.idle);
            this.primaryBlend.finishTransition();

            if (this.idle != null)
            {
                this.idle.resetFade();
            }
        }
        else if (this.active != null)
        {
            this.primaryBlend.select(this.active);
        }
    }

    /**
     * Create an action with default priority
     */
    public ActionPlayback createAction(ActionPlayback old, ActionConfig config, boolean looping)
    {
        return this.createAction(old, config, looping, 1);
    }

    /**
     * Create an action playback based on given arguments. This method
     * is used for creating actions so it was easier to tell which
     * actions are missing. Beside that, you can pass an old action so
     * in form merging situation it wouldn't interrupt animation.
     */
    public ActionPlayback createAction(ActionPlayback old, ActionConfig config, boolean looping, int priority)
    {
        java.util.Map<String, Clip> animations = this.model == null ? null : this.model.clips;

        if (animations == null)
        {
            return null;
        }

        Clip action = animations.get(config.name);

        /* If given action is missing, then omit creation of ActionPlayback */
        if (action == null)
        {
            return null;
        }

        /* If old is the same, then there is no point creating a new one */
        if (old != null && old.action == action)
        {
            old.config = config;
            old.setSpeed(1);

            return old;
        }

        return new ActionPlayback(action, config, looping, priority);
    }

    /**
     * Update animator. This method is responsible for updating action 
     * pipeline and also change current actions based on entity's state.
     */
    public void update(LivingEntity target)
    {
        /* Fix issue with forms sudden running action */
        if (this.prevX == Float.MAX_VALUE)
        {
            this.prevX = target.getX();
            this.prevZ = target.getZ();
            this.prevY = target.getY();
            this.wasOnGround = target.isOnGround();
        }

        this.controlActions(target);

        /* Update primary actions */
        if (this.basePre != null)
        {
            this.basePre.update();
        }

        if (this.basePost != null)
        {
            this.basePost.update();
        }

        this.primaryBlend.update();

        /* Update secondary actions */
        Iterator<ActionPlayback> it = this.actions.iterator();

        while (it.hasNext())
        {
            ActionPlayback action = it.next();

            action.update();

            if (action.finishedFading() && action.isFadingModeOut())
            {
                action.stopFade();
                it.remove();
            }
        }
    }

    /**
     * This method is designed specifically to isolate any controlling 
     * code (i.e. the ones that is responsible for switching between 
     * actions).
     */
    protected void controlActions(LivingEntity target)
    {
        Vec3d velocity = target.getVelocity();
        double dx = target.getX() - this.prevX;
        double dz = target.getZ() - this.prevZ;
        boolean moves = this.motionGate.update(dx, dz);
        float gaitSpeed = this.gaitDirection.update(dx, dz, target.bodyYaw);
        if (this.running != null) this.running.setSpeed(gaitSpeed);
        if (this.sprinting != null) this.sprinting.setSpeed(gaitSpeed);
        if (this.crouching != null) this.crouching.setSpeed(gaitSpeed);

        /* if (target.getHealth() <= 0)
        {
            this.setActiveAction(this.dying);
        }
        else if (target.isPlayerSleeping())
        {
            this.setActiveAction(this.sleeping);
        }
        else if (wet)
        {
            this.setActiveAction(!moves ? this.swimmingIdle : this.swimming);
        }
        else if (target.isRiding())
        {
            Entity riding = target.getRidingEntity();
            moves = Math.abs(riding.posX - this.prevX) > threshold || Math.abs(riding.posZ - this.prevZ) > threshold;

            this.prevX = riding.posX;
            this.prevZ = riding.posZ;
            this.setActiveAction(!moves ? this.ridingIdle : this.riding);
        }
        else if (creativeFlying || target.isElytraFlying())
        {
            this.setActiveAction(!moves ? this.flyingIdle : this.flying);
        } */
        if (false)
        {
            // TODO: implement more actions?
        }
        else
        {
            // A low ceiling can force the crouching pose without the sneak key.
            if (target.isInSneakingPose() || target.isSneaking())
            {
                this.setActiveAction(!moves ? this.crouchingIdle : this.crouching);
            }
            else if (!target.isOnGround() && velocity.y < 0 && target.fallDistance > 1.25)
            {
                this.setActiveAction(this.falling);
            }
            else if (target.isSprinting() && this.sprinting != null)
            {
                this.setActiveAction(this.sprinting);
            }
            else
            {
                this.setActiveAction(!moves ? this.idle : this.running);
            }

            if (target.isOnGround() && !this.wasOnGround && /* !target.isSprinting() && */ this.prevMY < -0.5)
            {
                this.addAction(this.land);
            }
        }

        // Remote player velocity can retain gravity even while the player stands.
        // A ground flag alone is insufficient: require actual upward displacement
        // since the previous tick, rather than the magnitude of cached velocity.
        if (!target.isClimbing() && !target.isOnGround() && this.wasOnGround
                && target.getY() - this.prevY > 0.2D)
        {
            ActionPlayback jump = this.jump1;

            if (this.jumpingCounter % 2 == 0 && this.jump2 != null)
            {
                jump = this.jump2;
            }

            this.addAction(jump);
            this.wasOnGround = false;

            this.jumpingCounter += 1;
        }

        float handSwingProgress = target.getHandSwingProgress(0F);

        if (handSwingProgress < this.prevHandSwing)
        {
            this.prevHandSwing = 0F;
        }

        if (handSwingProgress > 0F && this.prevHandSwing == 0F)
        {
            this.addAction(this.swipe);
        }

        this.prevHandSwing = handSwingProgress;

        this.prevX = target.getX();
        this.prevZ = target.getZ();
        this.prevY = target.getY();
        this.prevMY = velocity.y;

        this.wasOnGround = target.isOnGround();
    }

    /**
     * Set current active (primary) action 
     */
    public void setActiveAction(ActionPlayback action)
    {
        if (this.active == action || action == null)
        {
            return;
        }

        if (this.active != null && action.priority < this.active.priority)
        {
            return;
        }

        if (this.active != null)
        {
            this.lastActive = this.active;
        }

        this.active = action;
        this.primaryBlend.select(action);
    }

    public void addAction(ActionPlayback action)
    {
        this.addAction(action, true);
    }

    /**
     * Add an additional secondary action to the playback 
     */
    public void addAction(ActionPlayback action, boolean rewind)
    {
        if (action == null)
        {
            return;
        }

        if (this.actions.contains(action))
        {
            if (rewind)
            {
                action.rewind();
                action.fadeIn();
            }

            return;
        }

        action.rewind();
        action.fadeIn();
        this.actions.add(action);
    }

    /**
     * Apply currently running action pipeline onto given armature
     */
    public void applyActions(LivingEntity target, Rig armature, float transition)
    {
        if (this.basePre != null)
        {
            this.basePre.apply(target, armature, transition, 1F, false);
        }

        this.primaryBlend.apply(target, armature, transition);

        if (this.basePost != null)
        {
            this.basePost.apply(target, armature, transition, 1F, false);
        }

        for (ActionPlayback action : this.actions)
        {
            if (action.isFading())
            {
                action.apply(target, armature, transition, action.getFadeFactor(transition), true);
            }
            else
            {
                action.apply(target, armature, transition, 1F, true);
            }
        }
    }

    public void resetMotionGate() { this.motionGate.reset(); this.gaitDirection.reset(); }

    public void playAnimation(String name)
    {
        Clip animation = this.model.clips.get(name);

        this.addAction(new ActionPlayback(animation, new ActionConfig(), false, -1));
    }
}
