'use strict';

angular.module('cloudoptingApp')
    .controller('MenuController', function (SERVICE,
                                            $state, $scope, $document,
                                            Principal, Auth) {

        $scope.logoutButton = Principal.isAuthenticated();
        //$scope.name = Principal.isAuthenticated ? Principal.identity().login : '';
        if (Principal.isAuthenticated()) {
            Principal.identity().then(function (account) {
                $scope.name = account.login;
            });
        }

        $scope.logout = function(){
            Auth.logout();
            $state.go("login");
        };

        $scope.profile = function(){
            $state.go("profile");
        };

        $scope.dashboard = function(){
            $state.go("dashboard");
        };

        $scope.contact = function() {
            if($state.current.name == "catalogue"){
                var someElement = angular.element(document.getElementById('contact'));
                $document.scrollToElementAnimated( someElement, 30, 5000 );
            } else {
                $state.go('catalogue', { section: "contact" }, {reload: true} );
            }
        };

        $scope.$watch(
            function() {
                return Principal.isAuthenticated();
            },
            function(newVal, oldVal)
            {
                $scope.logoutButton = Principal.isAuthenticated();
                //$scope.name = Principal.isAuthenticated ? Principal.identity().login : '';
                if (Principal.isAuthenticated()) {
                    Principal.identity().then(function (account) {
                        $scope.name = account.login;
                    });
                }

                //TODO: Wrong. Check uses and delete it.
                $scope.isPublisher = function (){
                    return Principal.isInRole(SERVICE.ROLE.SUBSCRIBER);
                };

                $scope.logout = function(){
                    Auth.logout();
                    $state.go("catalogue");
                };
            },
            true
        );

        $scope.showMenu = function(item){
            if(Principal.isInRole(SERVICE.ROLE.ADMIN)){
                return true;
            }
            else if(Principal.isInRole(SERVICE.ROLE.OPERATOR)){
                if(item=='catalogue' || item=='detail' || item=='detail') {
                    return true;
                }
            }
            else if(Principal.isInRole(SERVICE.ROLE.PUBLISHER)){
                if(item=='catalogue' || item=='detail' || item=='instances' || item=='publish' || item=='list' || item=='toscaide') {
                    return true;
                }
            }
            else if(Principal.isInRole(SERVICE.ROLE.SUBSCRIBER)){
                if(item=='catalogue' || item=='detail' || item=='instances' || item=='subscriber' || item=='taylor') {
                    return true;
                }
            }
        };

    }
);
