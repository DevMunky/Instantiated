# Components

Components are ridiculously useful, yet just as complicated.

Components can do any number of things. Spawn mobs, open doors, send commands or send messages to players, or even trigger other components.

With this, configuring components can get complicated. This guide tries to keep things simple, so you can utilize components well.

I think this process is made a lot easier if you understand what is going on behind the scenes. Here is an example of a component:

```kotlin
class SpawnerComponent(
    locationTrait: LocationTrait,
    spawnerTrait: SpawnerTrait,
    override val uuid: UUID = UUID.randomUUID()
): DungeonComponent("spawner", listOf(locationTrait,spawnerTrait)){

    constructor(
        mob: DungeonMob,
        location: Vector3f,
        quantity: IntRange,
        radius: Float = 0f,
        uuid: UUID = UUID.randomUUID()
    ): this(LocationTrait(location), SpawnerTrait(mob, quantity, radius), uuid)

    override val codec: JsonCodec<DungeonComponent> = ComponentCodecs.SPAWNER as JsonCodec<DungeonComponent>
    override fun invoke(room: InstanceRoom){
        getTrait<SpawnerTrait>().invoke(room, this)
    }
}
```

> Some of the code here is used internally for things like serialization, therefore not all of it pertains to runtime functionality

Here you can see components are just a way to organize `Traits`. Traits give components their functionality.

Here is the `SpawnerTrait`, the main functionality of the `SpawnerComponent`:

```kotlin
class SpawnerTrait(
    val mob: DungeonMob,
    val quantity: IntRange = 1..1,
    val radius: Float = 0f
): FunctionalTrait("spawner") {

    override fun invoke(room: InstanceRoom, component: DungeonComponent?){
        mobSpawnLogic()
    }
}
```

Each component has a list of `Traits`. Each one of these is a new instance, that is mostly immutable.