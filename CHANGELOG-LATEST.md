### Added

- Blocks hung on a chain now have their own collision box (configurable).

### Changed

- Rewrote how chain collisions work to not use janky entities.
- Chain collisions are now enabled by default.

### Fixed

- Mobs can now pathfind through chains with collision.
- Numerous performance improvements.
- Fixed lighting turning black when chain knots are covered by blocks.
- Blocks can no longer be hung on a chain where they would clip into the world.
- Blocks can no longer be placed into the space occupied by a block hung on a chain.
- Fixed hung animated blocks not being animated.