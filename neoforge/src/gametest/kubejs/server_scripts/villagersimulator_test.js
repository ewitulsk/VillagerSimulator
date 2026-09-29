// Villager Simulator KubeJS integration, exercised by the villagersimulator_kubejs GameTests.
// Copied into runs/gametest/kubejs/server_scripts/ before each GameTest run.

// Events out, view reads, commands back: greet a new village by name and population.
VillagerSimEvents.recorded(e => {
  if (e.type == 'villagersimulator:village_founded' && e.detail == 'Scripton') {
    const village = VillagerSim.village('Scripton')
    VillagerSim.recordEvent(e.actor, 'kubejs:hello', 'Hello ' + e.detail + ' of ' + (village ? village.population : '?'))
  }
})

// A scenario written in JavaScript, run headless with /vs scenario run kubejs_hamlet.
VillagerSimEvents.scenarios(e => {
  e.add('kubejs_hamlet', 'A KubeJS scenario: a hamlet of 8 lives two days', s => {
    const village = s.spawnHamlet('Scriptford', 7, 8)
    s.warp('2d')
    s.expect('nobody starves', s.events('villagersimulator:starving') == 0)
    s.expect('eight residents', s.residents(village).size() == 8)
  })
})
