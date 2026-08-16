### Added

- Blocks hung on a chain now have their own collision box (configurable).

### Changed

- Rewrote how chain collisions work to not use janky entities.
- Chain collisions are now enabled by default.

### Fixed

- Mobs can now pathfind through chains with collision.
- Numerous performance improvements.
- Blocks can no longer be hung on a chain where they would clip into the world.
- Fixed hung animated blocks not being animated.