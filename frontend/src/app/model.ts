interface Player {
    id: string,
    name: string
}

interface RacingState {
    position: number,
    rank?: number,
    wpm: number
}

interface PlayerState {
    player: Player,
    state: RacingState
}

interface RaceState {
    current: PlayerState,
    others: PlayerState[]
}

interface RaceInit {
    quote: string,
    timeLeft: number
}

type GameOutput = RaceState | RaceInit

export {
    GameOutput,
    RaceInit,
    RaceState
}