import {BrowserModule} from '@angular/platform-browser';
import {NgModule} from '@angular/core';
import {AppComponent} from './app.component';
import {ApiService} from "./services/api.service";
import {HttpClientModule} from "@angular/common/http";
import {MatCardModule, MatFormFieldModule, MatSelectModule, MatToolbarModule} from "@angular/material";
import {BrowserAnimationsModule} from "@angular/platform-browser/animations";
import {FormsModule} from "@angular/forms";


@NgModule({
    declarations: [
        AppComponent
    ],
    imports: [
        HttpClientModule,
        BrowserModule,
        MatFormFieldModule,
        MatCardModule,
        MatSelectModule,
        BrowserAnimationsModule,
        MatToolbarModule,
        FormsModule
    ],
    providers: [ApiService],
    bootstrap: [AppComponent]
})
export class AppModule {
}
