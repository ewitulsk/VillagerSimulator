// kubejs/server_scripts/best_friends.js: announce in chat when two villagers become friends.
// Needs KubeJS and Villager Simulator (docs/MODDING.md).
VillagerSimEvents.recorded(e => {
  if (e.type == 'villagersimulator:became_friends') {
    VillagerSim.announce('[Villager Simulator] ' + e.detail + '!')
  }
})
