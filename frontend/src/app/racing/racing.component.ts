import { Component, OnInit } from '@angular/core';

import { GameService } from '../game.service'
import { RaceState } from 'app/model'

@Component({
  selector: 'app-racing',
  templateUrl: './racing.component.html'
})
export class RacingComponent implements OnInit {
  public raceState: RaceState;
  public raceLength: number;

  constructor(private _gameService: GameService) { }

  ngOnInit() {
    this._gameService.init.subscribe(
      init => this.raceLength = init.quote.split(" ").length
    )
    this._gameService.raceState.subscribe(
      state => {
        this.raceState = state
      }
    )
  }

  isWaitingForPlayers():boolean {
    return !this.raceState || !this.raceState.others || this.raceState.others.length == 0
  }
}
