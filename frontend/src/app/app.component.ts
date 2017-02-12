import { Component } from '@angular/core';

import { GameService } from 'app/game.service'

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html'
})
export class AppComponent {
  constructor(private _gameService: GameService) {}
  playerName: string;
  isPlayerSet: boolean;

  savePlayerName(): void {
    this._gameService.setPlayerName(this.playerName)
    this.isPlayerSet = true;
  }
}
